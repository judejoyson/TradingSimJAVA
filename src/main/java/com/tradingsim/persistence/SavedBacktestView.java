package com.tradingsim.persistence;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record SavedBacktestView(
        Long id,
        String symbol,
        String strategy,
        Instant createdAt,
        JsonNode result) {
}
