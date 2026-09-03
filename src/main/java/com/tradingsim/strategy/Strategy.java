package com.tradingsim.strategy;

import com.tradingsim.market.MarketDataPoint;

/**
 * Extension point for an automated strategy in the event-driven backtester.
 */
public interface Strategy {
    /**
     * Identifies the portfolio that owns this strategy's orders.
     */
    String accountId();

    /**
     * Reacts to one market update. Submit orders only through {@code context}
     * so a strategy stays decoupled from the matching engine.
     */
    void onMarketData(MarketDataPoint marketData, StrategyContext context);
}
