package com.tradingsim.persistence;

import com.tradingsim.security.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "saved_backtests")
public class SavedBacktest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private AppUser owner;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 40)
    private String strategy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected SavedBacktest() {
    }

    public SavedBacktest(AppUser owner, String symbol, String strategy, String resultJson) {
        this.owner = owner;
        this.symbol = symbol;
        this.strategy = strategy;
        this.resultJson = resultJson;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getStrategy() {
        return strategy;
    }

    public String getResultJson() {
        return resultJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
