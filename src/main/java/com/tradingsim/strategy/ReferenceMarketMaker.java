package com.tradingsim.strategy;

import com.tradingsim.market.MarketDataPoint;
import com.tradingsim.order.OrderRequest;
import com.tradingsim.order.Side;

public final class ReferenceMarketMaker implements Strategy {
    private final String accountId;
    private final long quoteQuantity;

    public ReferenceMarketMaker(String accountId, long quoteQuantity) {
        this.accountId = accountId;
        this.quoteQuantity = quoteQuantity;
    }

    @Override
    public String accountId() {
        return accountId;
    }

    @Override
    public void onMarketData(MarketDataPoint marketData, StrategyContext context) {
        context.submit(OrderRequest.limit(
                marketData.symbol(), Side.BUY, quoteQuantity, marketData.bid()));
        context.submit(OrderRequest.limit(
                marketData.symbol(), Side.SELL, quoteQuantity, marketData.ask()));
    }
}
