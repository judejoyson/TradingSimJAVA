package com.tradingsim.order;

import java.util.HashMap;
import java.util.Map;

public final class OrderBookManager {
    private final Map<String, OrderBook> books = new HashMap<>();

    public OrderBook bookFor(String symbol) {
        return books.computeIfAbsent(symbol, ignored -> new OrderBook());
    }
}
