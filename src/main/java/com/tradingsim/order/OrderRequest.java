package com.tradingsim.order;

import java.math.BigDecimal;
import java.util.Objects;

public record OrderRequest(
        String symbol,
        Side side,
        OrderType type,
        long quantity,
        BigDecimal limitPrice) {

    public OrderRequest {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(type, "type");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (type == OrderType.LIMIT && (limitPrice == null || limitPrice.signum() <= 0)) {
            throw new IllegalArgumentException("limit orders require a positive price");
        }
        if (type == OrderType.MARKET && limitPrice != null) {
            throw new IllegalArgumentException("market orders must not have a limit price");
        }
    }

    public static OrderRequest market(String symbol, Side side, long quantity) {
        return new OrderRequest(symbol, side, OrderType.MARKET, quantity, null);
    }

    public static OrderRequest limit(String symbol, Side side, long quantity, BigDecimal price) {
        return new OrderRequest(symbol, side, OrderType.LIMIT, quantity, price);
    }
}
