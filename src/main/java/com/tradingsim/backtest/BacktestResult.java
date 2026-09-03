package com.tradingsim.backtest;

import java.time.LocalDate;
import java.util.List;

public record BacktestResult(
        String market,
        String symbol,
        String instrumentName,
        String strategy,
        String strategyDescription,
        LocalDate startDate,
        LocalDate endDate,
        int candleCount,
        BacktestMetrics metrics,
        List<EquityPoint> equityCurve,
        List<BacktestExecution> executions) {
}
