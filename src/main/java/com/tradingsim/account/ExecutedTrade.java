package com.tradingsim.account;

import java.math.BigDecimal;
import java.time.Instant;

public record ExecutedTrade(
        long id,
        String symbol,
        TradeSide side,
        int quantity,
        BigDecimal price,
        BigDecimal total,
        Instant executedAt) {
}
