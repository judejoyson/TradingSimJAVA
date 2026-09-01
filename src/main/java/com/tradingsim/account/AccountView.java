package com.tradingsim.account;

import java.math.BigDecimal;
import java.util.List;

public record AccountView(
        BigDecimal startingCash,
        BigDecimal cash,
        BigDecimal positionsMarketValue,
        BigDecimal totalEquity,
        BigDecimal unrealizedProfitLoss,
        BigDecimal realizedProfitLoss,
        List<PositionView> positions,
        List<ExecutedTrade> recentTrades) {
}
