package com.tradingsim.engine;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * A small deterministic event loop used instead of the computer's real clock.
 *
 * <p>The sequence number makes equal-timestamp events execute in the order in
 * which they were scheduled.</p>
 */
public final class SimulatorEngine {
    private final PriorityQueue<ScheduledEvent> events = new PriorityQueue<>(
            Comparator.comparing(ScheduledEvent::timestamp)
                    .thenComparingLong(ScheduledEvent::sequence));
    private Instant currentTime = Instant.MIN;
    private long nextSequence;

    public Instant currentTime() {
        return currentTime;
    }

    /**
     * Adds future work to the queue. Past events are rejected because allowing
     * simulation time to move backward would make results ambiguous.
     */
    public void schedule(Instant timestamp, Runnable action) {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(action, "action");
        if (timestamp.isBefore(currentTime)) {
            throw new IllegalArgumentException("Cannot schedule an event in the past");
        }
        events.add(new ScheduledEvent(timestamp, nextSequence++, action));
    }

    /**
     * Advances the simulated clock and executes events until none remain.
     */
    public void run() {
        while (!events.isEmpty()) {
            ScheduledEvent event = events.remove();
            currentTime = event.timestamp();
            event.action().run();
        }
    }

    private record ScheduledEvent(Instant timestamp, long sequence, Runnable action) {
    }
}
