package com.tradingsim.replay;

import java.util.List;

public record ReplaySession(
        String market,
        String marketLabel,
        String symbol,
        String instrumentName,
        String timeframe,
        int timeframeMinutes,
        int pricePrecision,
        int initialBars,
        List<ReplayCandle> candles) {
}
