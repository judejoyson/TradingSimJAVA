package com.tradingsim.account;

import java.math.BigDecimal;

public record PositionView(
        String symbol,
        int quantity,
        BigDecimal averagePrice,
        BigDecimal costBasis,
        BigDecimal realizedProfitLoss) {
}
