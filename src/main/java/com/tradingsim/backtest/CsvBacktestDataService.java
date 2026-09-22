package com.tradingsim.backtest;

import com.tradingsim.web.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses user-provided daily OHLCV data for the historical strategy engine.
 */
@Service
public final class CsvBacktestDataService {
    static final long MAX_FILE_BYTES = 5 * 1024 * 1024;
    static final int MAX_ROWS = 25_000;
    private static final List<String> REQUIRED_COLUMNS =
            List.of("date", "open", "high", "low", "close", "volume");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/uuuu", Locale.US));

    public List<BacktestCandle> parse(MultipartFile file) {
        validateFile(file);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                file.getInputStream(),
                StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new BadRequestException("The CSV file is empty.");
            }
            Map<String, Integer> columns = headerColumns(removeBom(headerLine));
            List<BacktestCandle> candles = new ArrayList<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                if (candles.size() >= MAX_ROWS) {
                    throw new BadRequestException(
                            "The CSV file can contain at most " + MAX_ROWS + " data rows.");
                }
                candles.add(parseCandle(line, lineNumber, columns));
            }
            validateDates(candles);
            return List.copyOf(candles);
        } catch (IOException exception) {
            throw new BadRequestException("The CSV file could not be read.");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a CSV file containing historical candles.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BadRequestException("The CSV file must be 5 MB or smaller.");
        }
        String filename = file.getOriginalFilename();
        if (filename != null && !filename.toLowerCase(Locale.US).endsWith(".csv")) {
            throw new BadRequestException("The uploaded file must use the .csv extension.");
        }
    }

    private Map<String, Integer> headerColumns(String line) {
        List<String> headers = splitRow(line);
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            columns.put(normalizeHeader(headers.get(index)), index);
        }
        List<String> missing = REQUIRED_COLUMNS.stream()
                .filter(column -> !columns.containsKey(column))
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException(
                    "CSV header is missing: " + String.join(", ", missing)
                            + ". Required columns are date, open, high, low, close, volume.");
        }
        return columns;
    }

    private BacktestCandle parseCandle(
            String line,
            int lineNumber,
            Map<String, Integer> columns) {
        List<String> values = splitRow(line);
        try {
            LocalDate date = parseDate(value(values, columns, "date"));
            BigDecimal open = positiveDecimal(value(values, columns, "open"), "open");
            BigDecimal high = positiveDecimal(value(values, columns, "high"), "high");
            BigDecimal low = positiveDecimal(value(values, columns, "low"), "low");
            BigDecimal close = positiveDecimal(value(values, columns, "close"), "close");
            long volume = Long.parseLong(value(values, columns, "volume").trim());
            if (volume < 0) {
                throw new IllegalArgumentException("volume cannot be negative");
            }
            if (high.compareTo(open) < 0 || high.compareTo(close) < 0
                    || low.compareTo(open) > 0 || low.compareTo(close) > 0
                    || low.compareTo(high) > 0) {
                throw new IllegalArgumentException(
                        "high and low do not contain the open and close");
            }
            return new BacktestCandle(date, open, high, low, close, volume);
        } catch (DateTimeParseException | NumberFormatException exception) {
            throw rowError(lineNumber, "contains an invalid date or number");
        } catch (IllegalArgumentException exception) {
            throw rowError(lineNumber, exception.getMessage());
        } catch (IndexOutOfBoundsException exception) {
            throw rowError(lineNumber, "does not contain every required value");
        }
    }

    private void validateDates(List<BacktestCandle> candles) {
        if (candles.size() < 2) {
            throw new BadRequestException("The CSV file needs at least two candle rows.");
        }
        for (int index = 1; index < candles.size(); index++) {
            if (!candles.get(index).date().isAfter(candles.get(index - 1).date())) {
                throw new BadRequestException(
                        "CSV dates must be unique and sorted from oldest to newest.");
            }
        }
    }

    private List<String> splitRow(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(value.toString().trim());
                value.setLength(0);
            } else {
                value.append(character);
            }
        }
        if (quoted) {
            throw new BadRequestException("The CSV contains an unclosed quoted value.");
        }
        values.add(value.toString().trim());
        return values;
    }

    private String value(
            List<String> values,
            Map<String, Integer> columns,
            String column) {
        return values.get(columns.get(column));
    }

    private LocalDate parseDate(String value) {
        String date = value.trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(date, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next documented date format.
            }
        }
        throw new DateTimeParseException("Unsupported date", date, 0);
    }

    private BigDecimal positiveDecimal(String value, String column) {
        BigDecimal decimal = new BigDecimal(value.trim());
        if (decimal.signum() <= 0) {
            throw new IllegalArgumentException(column + " must be greater than zero");
        }
        return decimal;
    }

    private String normalizeHeader(String value) {
        return value.trim()
                .toLowerCase(Locale.US)
                .replace(" ", "")
                .replace("_", "");
    }

    private String removeBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private BadRequestException rowError(int lineNumber, String reason) {
        return new BadRequestException("CSV row " + lineNumber + " " + reason + ".");
    }
}
