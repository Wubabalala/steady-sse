package io.github.wubabalala.steadysse.metrics;

import io.github.wubabalala.steadysse.manager.SseConnectionManager;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * Spring Boot Actuator endpoint exposing SSE connection metrics.
 * <p>
 * Available at {@code /actuator/steadysse} when actuator is on the classpath.
 * Returns a {@link SseConnectionMetricsSnapshot} with both live state and cumulative counters.
 */
@Endpoint(id = "steadysse")
public class SseConnectionEndpoint {

    private final SseConnectionManager connectionManager;

    public SseConnectionEndpoint(SseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @ReadOperation
    public SseConnectionMetricsSnapshot metrics() {
        SseConnectionMetricsSnapshot snapshot = connectionManager.getMetricsSnapshot();
        if (snapshot != null) {
            return snapshot;
        }
        // Fallback: no metrics collector, return live state with zero counters
        return new SseConnectionMetricsSnapshot(
                connectionManager.getActiveCount(),
                connectionManager.getMaxConcurrent(),
                connectionManager.getAvailablePermits(),
                0, 0, 0, 0, 0
        );
    }
}
