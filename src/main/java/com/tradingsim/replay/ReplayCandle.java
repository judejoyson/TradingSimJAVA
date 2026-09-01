package com.tradingsim.replay;

import java.math.BigDecimal;

public record ReplayCandle(
        long time,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume) {
}
