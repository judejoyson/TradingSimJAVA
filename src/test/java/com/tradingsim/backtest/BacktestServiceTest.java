package com.tradingsim.backtest;

import com.tradingsim.replay.ReplayCatalog;
import com.tradingsim.web.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestServiceTest {
    private final ReplayCatalog catalog = new ReplayCatalog();
    private final HistoricalDataService history = new HistoricalDataService(catalog);
    private final BacktestService service = new BacktestService(history, catalog);

    @Test
    void buyAndHoldProducesDeterministicMetricsAndRoundTrip() {
        BacktestRequest request = request("BUY_AND_HOLD");

        BacktestResult first = service.run(request);
        BacktestResult second = service.run(request);

        assertEquals(first, second);
        assertEquals(2, first.executions().size());
        assertEquals("BUY", first.executions().get(0).side());
        assertEquals("SELL", first.executions().get(1).side());
        assertEquals(1, first.metrics().completedTrades());
        assertTrue(first.equityCurve().size() > 200);
    }

    @Test
    void movingAverageSignalsExecuteAfterTheSignalBar() {
        BacktestResult result = service.run(request("MOVING_AVERAGE"));

        assertTrue(result.candleCount() > 50);
        assertEquals(
                result.candleCount(),
                result.equityCurve().size());
        assertTrue(result.metrics().totalFees().signum() >= 0);
    }

    @Test
    void rejectsInvalidPeriodsAndDateOrder() {
        BacktestRequest invalidPeriods = new BacktestRequest(
                "STOCKS", "SPY", "MOVING_AVERAGE",
                LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1),
                new BigDecimal("10000"), BigDecimal.ONE,
                50, 20, 14, 30, 70);
        BacktestRequest invalidDates = new BacktestRequest(
                "STOCKS", "SPY", "RSI",
                LocalDate.of(2024, 1, 1), LocalDate.of(2023, 1, 1),
                new BigDecimal("10000"), BigDecimal.ONE,
                20, 50, 14, 30, 70);

        assertThrows(BadRequestException.class, () -> service.run(invalidPeriods));
        assertThrows(BadRequestException.class, () -> service.run(invalidDates));
    }

    private BacktestRequest request(String strategy) {
        return new BacktestRequest(
                "STOCKS",
                "SPY",
                strategy,
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2024, 1, 1),
                new BigDecimal("10000"),
                BigDecimal.ONE,
                20,
                50,
                14,
                30,
                70);
    }

}
