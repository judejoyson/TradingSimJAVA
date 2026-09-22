package com.tradingsim.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AccountView(
        AccountMode mode,
        BigDecimal startingCash,
        BigDecimal cash,
        BigDecimal positionsMarketValue,
        BigDecimal totalEquity,
        BigDecimal unrealizedProfitLoss,
        BigDecimal realizedProfitLoss,
        BigDecimal depositAmount,
        Instant nextDepositAt,
        boolean depositAvailable,
        BigDecimal totalDeposits,
        List<PositionView> positions,
        List<ExecutedTrade> recentTrades) {
}
