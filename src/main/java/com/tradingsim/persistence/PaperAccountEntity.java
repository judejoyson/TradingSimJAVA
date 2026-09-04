package com.tradingsim.persistence;

import com.tradingsim.account.AccountMode;
import com.tradingsim.security.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "paper_accounts")
public class PaperAccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    private AppUser owner;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal startingCash;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal cash;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal realizedProfitLoss;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountMode mode;

    @Column(nullable = false)
    private Instant lastDepositAt;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDeposits;

    protected PaperAccountEntity() {
    }

    public PaperAccountEntity(AppUser owner, BigDecimal startingCash) {
        this.owner = owner;
        this.startingCash = startingCash;
        this.cash = startingCash;
        this.realizedProfitLoss = BigDecimal.ZERO.setScale(2);
        this.mode = AccountMode.NORMAL;
        this.lastDepositAt = Instant.now();
        this.totalDeposits = BigDecimal.ZERO.setScale(2);
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public BigDecimal getStartingCash() {
        return startingCash;
    }

    public BigDecimal getCash() {
        return cash;
    }

    public void setCash(BigDecimal cash) {
        this.cash = cash;
    }

    public BigDecimal getRealizedProfitLoss() {
        return realizedProfitLoss;
    }

    public void setRealizedProfitLoss(BigDecimal realizedProfitLoss) {
        this.realizedProfitLoss = realizedProfitLoss;
    }

    public AccountMode getMode() {
        return mode;
    }

    public Instant getLastDepositAt() {
        return lastDepositAt;
    }

    public void setLastDepositAt(Instant lastDepositAt) {
        this.lastDepositAt = lastDepositAt;
    }

    public BigDecimal getTotalDeposits() {
        return totalDeposits;
    }

    public void setTotalDeposits(BigDecimal totalDeposits) {
        this.totalDeposits = totalDeposits;
    }

    public void reset(BigDecimal newStartingCash, AccountMode newMode, Instant now) {
        startingCash = newStartingCash;
        cash = startingCash;
        realizedProfitLoss = BigDecimal.ZERO.setScale(2);
        mode = newMode;
        lastDepositAt = now;
        totalDeposits = BigDecimal.ZERO.setScale(2);
    }
}
