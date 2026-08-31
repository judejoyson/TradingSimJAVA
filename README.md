# TradingSimJAVA

A deterministic, event-driven trading simulator starter written in Java 17.

## Architecture

```text
SimulatorEngine (clock + priority event queue)
    -> MarketDataFeed (CSV today; REST/WebSocket adapters can implement the interface)
    -> TradingSimulator (dispatches ticks to strategies)
    -> OrderBookManager (one limit order book per symbol)
    -> MatchingEngine (price-time priority and partial fills)
    -> PortfolioManager (cash and positions)
    -> SimulationReport (orders, trades, and account snapshots)
```

The simulator deliberately separates market-data ingestion, matching, accounting,
and strategies. Events with identical timestamps execute in insertion order, making
backtests reproducible.

## Run the sample

Requirements: JDK 17+ and Maven 3.9+.

```shell
mvn test
mvn exec:java
```

To replay another CSV file:

```shell
mvn exec:java -Dexec.args="path/to/market-data.csv"
```

CSV files use this schema:

```csv
timestamp,symbol,bid,ask,last
2026-01-02T14:30:00Z,AAPL,199.90,200.10,200.00
```

## Implement a strategy

Implement `Strategy` and submit orders through the supplied `StrategyContext`:

```java
public final class MyStrategy implements Strategy {
    @Override
    public String accountId() {
        return "MY_ACCOUNT";
    }

    @Override
    public void onMarketData(MarketDataPoint data, StrategyContext context) {
        if (data.last().compareTo(new BigDecimal("100")) < 0) {
            context.submit(OrderRequest.market(data.symbol(), Side.BUY, 10));
        }
    }
}
```

Register it before running:

```java
simulator.addStrategy(new MyStrategy(), new BigDecimal("10000"));
```

## Suggested next milestones

1. Add order cancellation, order status, and execution reports.
2. Add pre-trade risk checks for cash, position, and exposure limits.
3. Mark portfolios to market and calculate realized/unrealized P&L.
4. Add REST and WebSocket market-data adapters behind `MarketDataFeed`.
5. Persist trades and equity curves for charting and performance metrics.