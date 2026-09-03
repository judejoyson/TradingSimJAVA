package com.tradingsim.backtest;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BacktestRequest(
        @NotBlank String market,
        @NotBlank String symbol,
        @NotBlank String strategy,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @DecimalMin("100.00") @DecimalMax("1000000000.00") BigDecimal startingCash,
        @DecimalMin("0.0") @DecimalMax("1000.0") BigDecimal feeRateBps,
        @Min(2) @Max(200) Integer fastPeriod,
        @Min(3) @Max(400) Integer slowPeriod,
        @Min(2) @Max(100) Integer rsiPeriod,
        @Min(1) @Max(49) Integer oversold,
        @Min(51) @Max(99) Integer overbought) {
}
