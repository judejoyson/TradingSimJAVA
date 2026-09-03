package com.tradingsim.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BacktestCandle(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume) {
}
