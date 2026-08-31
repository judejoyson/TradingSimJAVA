package com.tradingsim.market;

import com.tradingsim.engine.SimulatorEngine;

import java.io.IOException;
import java.util.function.Consumer;

public interface MarketDataFeed {
    void scheduleInto(SimulatorEngine engine, Consumer<MarketDataPoint> consumer) throws IOException;
}
