package com.tradingsim.persistence;

import java.time.Instant;

public record JournalView(
        Long id,
        String title,
        String notes,
        Instant createdAt) {

    static JournalView from(JournalEntry entry) {
        return new JournalView(
                entry.getId(),
                entry.getTitle(),
                entry.getNotes(),
                entry.getCreatedAt());
    }
}
