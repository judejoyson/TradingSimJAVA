package com.tradingsim.report;

import com.tradingsim.order.Trade;
import com.tradingsim.portfolio.AccountSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SimulationReport {
    private final List<Trade> trades = new ArrayList<>();
    private long marketDataPointCount;
    private long submittedOrderCount;

    public void recordMarketDataPoint() {
        marketDataPointCount++;
    }

    public void recordOrder() {
        submittedOrderCount++;
    }

    public void recordTrades(List<Trade> newTrades) {
        trades.addAll(newTrades);
    }

    public List<Trade> trades() {
        return List.copyOf(trades);
    }

    public String format(Map<String, AccountSnapshot> accounts) {
        StringBuilder output = new StringBuilder()
                .append("Market data points: ").append(marketDataPointCount).append(System.lineSeparator())
                .append("Orders submitted: ").append(submittedOrderCount).append(System.lineSeparator())
                .append("Trades executed: ").append(trades.size()).append(System.lineSeparator());
        accounts.values().stream()
                .sorted(java.util.Comparator.comparing(AccountSnapshot::accountId))
                .forEach(account -> output
                        .append(account.accountId())
                        .append(" cash=").append(account.cash())
                        .append(" positions=").append(account.positions())
                        .append(System.lineSeparator()));
        return output.toString();
    }
}
