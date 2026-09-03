package com.tradingsim.backtest;

import com.tradingsim.web.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvBacktestDataServiceTest {
    private final CsvBacktestDataService service = new CsvBacktestDataService();

    @Test
    void parsesHeadersInAnyOrderAndSupportedDates() {
        String csv = """
                Volume,Close,Low,Date,High,Open
                1000,101.50,99.00,1/2/2025,102.00,100.00
                1200,102.50,100.50,2025-01-03,103.00,101.50
                """;

        List<BacktestCandle> candles = service.parse(file(csv));

        assertEquals(2, candles.size());
        assertEquals(LocalDate.of(2025, 1, 2), candles.get(0).date());
        assertEquals("102.50", candles.get(1).close().toPlainString());
    }

    @Test
    void rejectsInvalidPricesAndDateOrder() {
        String invalidPrice = """
                date,open,high,low,close,volume
                2025-01-02,100,99,98,101,1000
                2025-01-03,101,103,100,102,1000
                """;
        String unsorted = """
                date,open,high,low,close,volume
                2025-01-03,100,102,99,101,1000
                2025-01-02,101,103,100,102,1000
                """;

        assertThrows(BadRequestException.class, () -> service.parse(file(invalidPrice)));
        assertThrows(BadRequestException.class, () -> service.parse(file(unsorted)));
    }

    @Test
    void rejectsMissingRequiredColumns() {
        String csv = """
                date,open,high,low,close
                2025-01-02,100,102,99,101
                2025-01-03,101,103,100,102
                """;

        assertThrows(BadRequestException.class, () -> service.parse(file(csv)));
    }

    private MockMultipartFile file(String content) {
        return new MockMultipartFile(
                "file",
                "history.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
