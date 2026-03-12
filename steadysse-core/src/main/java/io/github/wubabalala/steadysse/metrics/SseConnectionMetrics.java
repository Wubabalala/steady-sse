package io.github.wubabalala.steadysse.metrics;

import io.github.wubabalala.steadysse.lifecycle.StreamEndStatus;

import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe cumulative metrics collector for SSE connections.
 * <p>
 * Tracks both completion counts by status and rejection counts.
 * Use {@link #snapshot(int, int, int)} to create an immutable point-in-time view
 * combined with live connection state from the connection manager.
 */
public class SseConnectionMetrics {

    private final LongAdder totalCompleted = new LongAdder();
    private final LongAdder totalErrors = new LongAdder();
    private final LongAdder totalTimeouts = new LongAdder();
    private final LongAdder totalCancelled = new LongAdder();
    private final LongAdder totalRejected = new LongAdder();

    /**
     * Record a connection completion. Increments both the total counter
     * and the status-specific counter.
     */
    public void recordCompletion(StreamEndStatus status) {
        totalCompleted.increment();
        switch (status) {
            case ERROR -> totalErrors.increment();
            case TIMEOUT -> totalTimeouts.increment();
            case CANCELLED -> totalCancelled.increment();
            case SUCCESS -> { /* no additional counter */ }
        }
    }

    /**
     * Record a connection rejection (concurrency limit reached).
     */
    public void recordRejection() {
        totalRejected.increment();
    }

    /**
     * Create an immutable snapshot combining cumulative counters with live connection state.
     *
     * @param active           current active connection count
     * @param maxConcurrent    configured maximum
     * @param availablePermits current available semaphore permits
     */
    public SseConnectionMetricsSnapshot snapshot(int active, int maxConcurrent, int availablePermits) {
        return new SseConnectionMetricsSnapshot(
                active,
                maxConcurrent,
                availablePermits,
                totalCompleted.sum(),
                totalErrors.sum(),
                totalTimeouts.sum(),
                totalCancelled.sum(),
                totalRejected.sum()
        );
    }
}
