package com.tradingsim.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record Order(
        long id,
        long sequence,
        String accountId,
        String symbol,
        Side side,
        OrderType type,
        long quantity,
        BigDecimal limitPrice,
        Instant submittedAt) {

    public Order {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(submittedAt, "submittedAt");
        if (id <= 0 || sequence < 0 || quantity <= 0) {
            throw new IllegalArgumentException("invalid order identifiers or quantity");
        }
        if (type == OrderType.LIMIT && (limitPrice == null || limitPrice.signum() <= 0)) {
            throw new IllegalArgumentException("limit orders require a positive price");
        }
        if (type == OrderType.MARKET && limitPrice != null) {
            throw new IllegalArgumentException("market orders must not have a limit price");
        }
    }
}
