package com.tradingsim.backtest;

import java.math.BigDecimal;

public record BacktestMetrics(
        BigDecimal endingEquity,
        BigDecimal netProfit,
        BigDecimal totalReturnPercent,
        BigDecimal benchmarkReturnPercent,
        BigDecimal maxDrawdownPercent,
        BigDecimal annualizedReturnPercent,
        BigDecimal totalFees,
        int completedTrades,
        int winningTrades,
        int losingTrades,
        BigDecimal winRatePercent) {
}
