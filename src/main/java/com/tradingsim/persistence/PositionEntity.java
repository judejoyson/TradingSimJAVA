package com.tradingsim.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
        name = "paper_positions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "symbol"}))
public class PositionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private PaperAccountEntity account;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal averagePrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal realizedProfitLoss;

    protected PositionEntity() {
    }

    public PositionEntity(PaperAccountEntity account, String symbol) {
        this.account = account;
        this.symbol = symbol;
        this.quantity = 0;
        this.averagePrice = BigDecimal.ZERO;
        this.realizedProfitLoss = BigDecimal.ZERO.setScale(2);
    }

    public String getSymbol() {
        return symbol;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAveragePrice() {
        return averagePrice;
    }

    public void setAveragePrice(BigDecimal averagePrice) {
        this.averagePrice = averagePrice;
    }

    public BigDecimal getRealizedProfitLoss() {
        return realizedProfitLoss;
    }

    public void setRealizedProfitLoss(BigDecimal realizedProfitLoss) {
        this.realizedProfitLoss = realizedProfitLoss;
    }
}
