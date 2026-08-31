package com.tradingsim.quote;

import java.math.BigDecimal;
import java.time.Instant;

public record StockQuote(
        String symbol,
        BigDecimal currentPrice,
        BigDecimal change,
        BigDecimal percentChange,
        BigDecimal high,
        BigDecimal low,
        BigDecimal open,
        BigDecimal previousClose,
        Instant marketTimestamp) {
}
