package com.tradingsim.report;

import com.tradingsim.order.Trade;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects immutable-facing trade results from one backtest run.
 */
public final class SimulationReport {
    private final List<Trade> trades = new ArrayList<>();

    public void recordTrades(List<Trade> newTrades) {
        trades.addAll(newTrades);
    }

    public List<Trade> trades() {
        return List.copyOf(trades);
    }
}
