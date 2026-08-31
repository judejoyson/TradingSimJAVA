package com.tradingsim.account;

import com.tradingsim.quote.Symbols;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PlaceOrderRequest(
        @NotNull
        @Pattern(regexp = Symbols.VALID_SYMBOL_PATTERN, message = "must be a valid stock symbol")
        String symbol,
        @NotNull TradeSide side,
        @Min(1) @Max(100_000) int quantity) {
}
