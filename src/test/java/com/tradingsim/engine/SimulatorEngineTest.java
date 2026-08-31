package com.tradingsim.engine;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulatorEngineTest {
    @Test
    void processesEventsByTimeAndThenInsertionOrder() {
        SimulatorEngine engine = new SimulatorEngine();
        List<String> results = new ArrayList<>();
        Instant later = Instant.parse("2026-01-01T00:00:01Z");
        Instant earlier = Instant.parse("2026-01-01T00:00:00Z");

        engine.schedule(later, () -> results.add("later"));
        engine.schedule(earlier, () -> results.add("first"));
        engine.schedule(earlier, () -> results.add("second"));
        engine.run();

        assertEquals(List.of("first", "second", "later"), results);
        assertEquals(later, engine.currentTime());
    }
}
