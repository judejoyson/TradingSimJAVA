package com.tradingsim.market;

import com.tradingsim.engine.SimulatorEngine;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Adapter boundary for sources that can schedule market data into a simulation.
 *
 * <p>Implement this interface to add another source without changing the
 * simulator coordinator.</p>
 */
public interface MarketDataFeed {
    void scheduleInto(SimulatorEngine engine, Consumer<MarketDataPoint> consumer) throws IOException;
}
