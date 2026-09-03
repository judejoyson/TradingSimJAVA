package com.tradingsim.backtest;

import com.tradingsim.replay.MarketOption;

import java.time.LocalDate;
import java.util.List;

public record BacktestOptions(
        List<MarketOption> markets,
        List<StrategyOption> strategies,
        LocalDate earliestDate,
        LocalDate latestDate) {
}
