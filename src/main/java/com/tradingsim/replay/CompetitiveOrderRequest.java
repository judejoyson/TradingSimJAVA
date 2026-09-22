package com.tradingsim.replay;

import com.tradingsim.account.TradeSide;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CompetitiveOrderRequest(
        @NotNull UUID sessionId,
        @NotNull TradeSide side,
        @Min(1) @Max(100_000) int quantity) {
}
