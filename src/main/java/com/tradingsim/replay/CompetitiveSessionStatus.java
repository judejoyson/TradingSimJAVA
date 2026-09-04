package com.tradingsim.replay;

public record CompetitiveSessionStatus(
        boolean active,
        CompetitiveSessionView session) {
}
