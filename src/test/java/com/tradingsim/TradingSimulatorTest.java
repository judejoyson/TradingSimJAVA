package com.tradingsim;

import com.tradingsim.market.MarketDataFeed;
import com.tradingsim.market.MarketDataPoint;
import com.tradingsim.report.SimulationReport;
import com.tradingsim.strategy.BuyAndHoldStrategy;
import com.tradingsim.strategy.ReferenceMarketMaker;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradingSimulatorTest {
    @Test
    void runsMarketDataThroughStrategiesMatchingAndPortfolioAccounting() throws Exception {
        MarketDataPoint point = new MarketDataPoint(
                Instant.parse("2026-01-01T00:00:00Z"),
                "AAPL",
                new BigDecimal("99.00"),
                new BigDecimal("101.00"),
                new BigDecimal("100.00"));
        MarketDataFeed feed = (engine, consumer) ->
                engine.schedule(point.timestamp(), () -> consumer.accept(point));

        TradingSimulator simulator = new TradingSimulator();
        simulator.addStrategy(
                new ReferenceMarketMaker("MARKET", 100),
                new BigDecimal("100000"));
        simulator.addStrategy(
                new BuyAndHoldStrategy("INVESTOR", 10),
                new BigDecimal("10000"));

        SimulationReport report = simulator.run(feed);

        assertEquals(1, report.trades().size());
        assertEquals(new BigDecimal("8990.00"), simulator.portfolios().snapshot("INVESTOR").cash());
        assertEquals(10L, simulator.portfolios().snapshot("INVESTOR").positions().get("AAPL"));
    }
}
