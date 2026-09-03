package com.tradingsim.market;

import com.tradingsim.engine.SimulatorEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Reads timestamp, symbol, bid, ask, and last price columns from a CSV file.
 */
public final class CsvMarketDataFeed implements MarketDataFeed {
    private final Path path;

    public CsvMarketDataFeed(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    @Override
    public void scheduleInto(SimulatorEngine engine, Consumer<MarketDataPoint> consumer) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || (lineNumber == 1 && line.startsWith("timestamp,"))) {
                    continue;
                }
                MarketDataPoint point = parse(line, lineNumber);
                // Parsing and execution are separate: the feed first populates
                // the event queue, then SimulatorEngine decides event order.
                engine.schedule(point.timestamp(), () -> consumer.accept(point));
            }
        }
    }

    private MarketDataPoint parse(String line, int lineNumber) throws IOException {
        String[] fields = line.split(",", -1);
        if (fields.length != 5) {
            throw new IOException("Expected 5 columns at CSV line " + lineNumber);
        }
        try {
            return new MarketDataPoint(
                    Instant.parse(fields[0].trim()),
                    fields[1].trim(),
                    new BigDecimal(fields[2].trim()),
                    new BigDecimal(fields[3].trim()),
                    new BigDecimal(fields[4].trim()));
        } catch (RuntimeException exception) {
            throw new IOException("Invalid market data at CSV line " + lineNumber, exception);
        }
    }
}
