package com.tradingsim.portfolio;

import com.tradingsim.order.Trade;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PortfolioManager {
    private final Map<String, Account> accounts = new LinkedHashMap<>();

    public void registerAccount(String accountId, BigDecimal initialCash) {
        if (accounts.putIfAbsent(accountId, new Account(initialCash)) != null) {
            throw new IllegalArgumentException("Account already exists: " + accountId);
        }
    }

    public void apply(Trade trade) {
        BigDecimal notional = trade.price().multiply(BigDecimal.valueOf(trade.quantity()));
        Account buyer = account(trade.buyerAccountId());
        Account seller = account(trade.sellerAccountId());
        buyer.cash = buyer.cash.subtract(notional);
        seller.cash = seller.cash.add(notional);
        buyer.positions.merge(trade.symbol(), trade.quantity(), Long::sum);
        seller.positions.merge(trade.symbol(), -trade.quantity(), Long::sum);
    }

    public AccountSnapshot snapshot(String accountId) {
        Account account = account(accountId);
        return new AccountSnapshot(accountId, account.cash, Map.copyOf(account.positions));
    }

    public Map<String, AccountSnapshot> snapshots() {
        Map<String, AccountSnapshot> result = new LinkedHashMap<>();
        accounts.keySet().forEach(id -> result.put(id, snapshot(id)));
        return Map.copyOf(result);
    }

    private Account account(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Unknown account: " + accountId);
        }
        return account;
    }

    private static final class Account {
        private BigDecimal cash;
        private final Map<String, Long> positions = new HashMap<>();

        private Account(BigDecimal initialCash) {
            this.cash = initialCash;
        }
    }
}
