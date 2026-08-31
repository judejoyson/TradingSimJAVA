package com.tradingsim.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record MarketDataPoint(
        Instant timestamp,
        String symbol,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal last) {

    public MarketDataPoint {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(bid, "bid");
        Objects.requireNonNull(ask, "ask");
        Objects.requireNonNull(last, "last");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (bid.signum() <= 0 || ask.signum() <= 0 || last.signum() <= 0) {
            throw new IllegalArgumentException("prices must be positive");
        }
        if (bid.compareTo(ask) > 0) {
            throw new IllegalArgumentException("bid must not exceed ask");
        }
    }
}
