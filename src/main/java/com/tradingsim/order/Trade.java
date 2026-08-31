package com.tradingsim.order;

import java.math.BigDecimal;
import java.time.Instant;

public record Trade(
        long buyOrderId,
        long sellOrderId,
        String buyerAccountId,
        String sellerAccountId,
        String symbol,
        long quantity,
        BigDecimal price,
        Instant timestamp) {
}
