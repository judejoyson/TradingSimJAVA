package com.tradingsim.strategy;

import com.tradingsim.order.OrderRequest;

/**
 * The narrow capability a strategy receives for submitting an order.
 */
@FunctionalInterface
public interface StrategyContext {
    void submit(OrderRequest request);
}
