package io.github.wubabalala.steadysse.metrics;

/**
 * Immutable point-in-time snapshot of SSE connection metrics.
 * <p>
 * Contains both live connection state (active/max/available) and
 * cumulative counters (completed/errors/timeouts/cancelled/rejected).
 *
 * @param active           current number of active connections
 * @param maxConcurrent    configured maximum concurrent connections
 * @param availablePermits remaining semaphore permits
 * @param totalCompleted   cumulative count of all completed connections (any exit path)
 * @param totalErrors      cumulative count of connections ended with ERROR status
 * @param totalTimeouts    cumulative count of connections ended with TIMEOUT status
 * @param totalCancelled   cumulative count of connections ended with CANCELLED status
 * @param totalRejected    cumulative count of connections rejected due to concurrency limit
 */
public record SseConnectionMetricsSnapshot(
        int active,
        int maxConcurrent,
        int availablePermits,
        long totalCompleted,
        long totalErrors,
        long totalTimeouts,
        long totalCancelled,
        long totalRejected
) {
}
