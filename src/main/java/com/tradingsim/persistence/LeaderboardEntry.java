package com.tradingsim.persistence;

import java.math.BigDecimal;

public record LeaderboardEntry(
        int rank,
        String displayName,
        BigDecimal returnPercent,
        BigDecimal sharpeRatio,
        BigDecimal consistencyPercent,
        int completedTrades) {
}
