package com.tradingsim;

import com.tradingsim.engine.SimulatorEngine;
import com.tradingsim.market.MarketDataFeed;
import com.tradingsim.market.MarketDataPoint;
import com.tradingsim.order.MatchingEngine;
import com.tradingsim.order.Order;
import com.tradingsim.order.OrderBookManager;
import com.tradingsim.order.OrderRequest;
import com.tradingsim.order.Trade;
import com.tradingsim.portfolio.PortfolioManager;
import com.tradingsim.report.SimulationReport;
import com.tradingsim.strategy.Strategy;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Connects the original event-driven backtesting components into one workflow.
 *
 * <p>This coordinator intentionally contains little business logic. It moves
 * market data through strategies, matching, accounting, and reporting in that
 * order. ReplayLab's browser-driven replay uses the {@code replay} package
 * instead.</p>
 */
public final class TradingSimulator {
    private final SimulatorEngine engine = new SimulatorEngine();
    private final PortfolioManager portfolios = new PortfolioManager();
    private final MatchingEngine matchingEngine = new MatchingEngine(new OrderBookManager());
    private final SimulationReport report = new SimulationReport();
    private final List<Strategy> strategies = new ArrayList<>();
    private long nextOrderId = 1;
    private long nextOrderSequence;

    /**
     * Registers a strategy and creates the account used for all its orders.
     */
    public void addStrategy(Strategy strategy, BigDecimal initialCash) {
        portfolios.registerAccount(strategy.accountId(), initialCash);
        strategies.add(strategy);
    }

    /**
     * Schedules every feed item and processes events until the queue is empty.
     */
    public SimulationReport run(MarketDataFeed feed) throws IOException {
        feed.scheduleInto(engine, this::onMarketData);
        engine.run();
        return report;
    }

    public PortfolioManager portfolios() {
        return portfolios;
    }

    private void onMarketData(MarketDataPoint marketData) {
        // Each strategy sees the same point before simulation time advances.
        for (Strategy strategy : strategies) {
            strategy.onMarketData(
                    marketData,
                    request -> submit(strategy.accountId(), request));
        }
    }

    private void submit(String accountId, OrderRequest request) {
        // Sequence, not wall-clock time, breaks ties between orders submitted
        // during the same event and therefore preserves price-time priority.
        Order order = new Order(
                nextOrderId++,
                nextOrderSequence++,
                accountId,
                request.symbol(),
                request.side(),
                request.type(),
                request.quantity(),
                request.limitPrice(),
                engine.currentTime());
        List<Trade> trades = matchingEngine.submit(order);
        trades.forEach(portfolios::apply);
        report.recordTrades(trades);
    }
}
