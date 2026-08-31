package com.tradingsim.strategy;

import com.tradingsim.market.MarketDataPoint;

public interface Strategy {
    String accountId();

    void onMarketData(MarketDataPoint marketData, StrategyContext context);
}
