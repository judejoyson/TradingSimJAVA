package com.tradingsim.replay;

import com.tradingsim.security.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "competitive_sessions")
public class CompetitiveSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    private AppUser owner;

    @Column(nullable = false, unique = true, length = 36)
    private String sessionId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 10)
    private String timeframe;

    @Column(nullable = false)
    private long randomSeed;

    @Column(nullable = false)
    private int cursorPosition;

    @Column(nullable = false)
    private int entryQuantity;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false)
    private Instant createdAt;

    protected CompetitiveSessionEntity() {
    }

    public CompetitiveSessionEntity(
            AppUser owner,
            UUID sessionId,
            String symbol,
            String timeframe,
            long randomSeed,
            int cursorPosition) {
        this.owner = owner;
        replace(sessionId, symbol, timeframe, randomSeed, cursorPosition);
    }

    public UUID getSessionId() {
        return UUID.fromString(sessionId);
    }

    public String getSymbol() {
        return symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public int getCursorPosition() {
        return cursorPosition;
    }

    public void advance() {
        cursorPosition++;
    }

    public void replace(
            UUID newSessionId,
            String newSymbol,
            String newTimeframe,
            long newRandomSeed,
            int newCursorPosition) {
        sessionId = newSessionId.toString();
        symbol = newSymbol;
        timeframe = newTimeframe;
        randomSeed = newRandomSeed;
        cursorPosition = newCursorPosition;
        entryQuantity = 0;
        completed = false;
        createdAt = Instant.now();
    }

    public int getEntryQuantity() {
        return entryQuantity;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void recordEntry(int quantity) {
        entryQuantity = quantity;
    }

    public void complete() {
        completed = true;
    }
}
