package com.tradingsim.strategy;

import com.tradingsim.order.OrderRequest;

@FunctionalInterface
public interface StrategyContext {
    void submit(OrderRequest request);
}
