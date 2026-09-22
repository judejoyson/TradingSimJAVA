package com.tradingsim.strategy;

import com.tradingsim.market.MarketDataPoint;
import com.tradingsim.order.OrderRequest;
import com.tradingsim.order.Side;

import java.util.HashSet;
import java.util.Set;

public final class BuyAndHoldStrategy implements Strategy {
    private final String accountId;
    private final long quantity;
    private final Set<String> purchasedSymbols = new HashSet<>();

    public BuyAndHoldStrategy(String accountId, long quantity) {
        this.accountId = accountId;
        this.quantity = quantity;
    }

    @Override
    public String accountId() {
        return accountId;
    }

    @Override
    public void onMarketData(MarketDataPoint marketData, StrategyContext context) {
        if (purchasedSymbols.add(marketData.symbol())) {
            context.submit(OrderRequest.market(marketData.symbol(), Side.BUY, quantity));
        }
    }
}
