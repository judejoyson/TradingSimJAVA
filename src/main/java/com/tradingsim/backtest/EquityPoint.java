package com.tradingsim.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EquityPoint(
        LocalDate date,
        BigDecimal equity,
        BigDecimal benchmarkEquity,
        BigDecimal drawdownPercent) {
}
