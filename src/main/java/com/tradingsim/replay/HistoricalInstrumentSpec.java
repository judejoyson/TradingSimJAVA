package com.tradingsim.replay;

import java.math.BigDecimal;

/**
 * Minimal instrument information shared with historical-data generators.
 */
public record HistoricalInstrumentSpec(
        String market,
        String marketLabel,
        String symbol,
        String name,
        int pricePrecision,
        BigDecimal startingPrice,
        double volatility) {
}
