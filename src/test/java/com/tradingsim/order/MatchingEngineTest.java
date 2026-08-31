package com.tradingsim.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchingEngineTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void matchesUsingPriceTimePriorityAndSupportsPartialFills() {
        MatchingEngine engine = new MatchingEngine(new OrderBookManager());
        engine.submit(limitOrder(1, 0, "SELLER_ONE", Side.SELL, 10, "101.00"));
        engine.submit(limitOrder(2, 1, "SELLER_TWO", Side.SELL, 10, "101.00"));

        Order marketBuy = new Order(
                3, 2, "BUYER", "AAPL", Side.BUY, OrderType.MARKET, 15, null, NOW);
        List<Trade> trades = engine.submit(marketBuy);

        assertEquals(2, trades.size());
        assertEquals(1, trades.get(0).sellOrderId());
        assertEquals(10, trades.get(0).quantity());
        assertEquals(2, trades.get(1).sellOrderId());
        assertEquals(5, trades.get(1).quantity());
        assertEquals(new BigDecimal("101.00"), trades.get(0).price());
    }

    private Order limitOrder(
            long id,
            long sequence,
            String account,
            Side side,
            long quantity,
            String price) {
        return new Order(
                id,
                sequence,
                account,
                "AAPL",
                side,
                OrderType.LIMIT,
                quantity,
                new BigDecimal(price),
                NOW);
    }
}
