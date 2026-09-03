package com.tradingsim.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BacktestExecution(
        LocalDate date,
        String side,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal fee,
        String reason) {
}
