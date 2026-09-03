package com.tradingsim.replay;

import com.tradingsim.web.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayDataServiceTest {
    private final ReplayDataService service = new ReplayDataService(new ReplayCatalog());

    @Test
    void createsDeterministicValidCandles() {
        ReplaySession first = service.createSession("stocks", "SPY", "5m");
        ReplaySession second = service.createSession("STOCKS", "SPY", "5m");

        assertEquals(ReplayDataService.CANDLE_COUNT, first.candles().size());
        assertEquals(first.candles(), second.candles());
        assertEquals(ReplayDataService.INITIAL_BARS, first.initialBars());
        assertEquals(2.0, first.executionProfile().spreadBps());
        assertEquals(0.05, first.executionProfile().maxVolumeParticipationPercent());
        assertTrue(first.candles().stream().allMatch(candle ->
                candle.low().compareTo(candle.open()) <= 0
                        && candle.low().compareTo(candle.close()) <= 0
                        && candle.high().compareTo(candle.open()) >= 0
                        && candle.high().compareTo(candle.close()) >= 0));
    }

    @Test
    void rejectsMismatchedMarketAndInstrument() {
        assertThrows(
                BadRequestException.class,
                () -> service.createSession("FOREX", "SPY", "5m"));
    }

    @Test
    void exposesAllMarketsAndTimeframes() {
        ReplayOptions options = service.options();

        assertEquals(3, options.markets().size());
        assertEquals(4, options.timeframes().size());
        assertEquals("SPY", options.markets().get(0).instruments().get(0).symbol());
    }
}
