package com.tradingsim.portfolio;

import java.math.BigDecimal;
import java.util.Map;

public record AccountSnapshot(
        String accountId,
        BigDecimal cash,
        Map<String, Long> positions) {
}
