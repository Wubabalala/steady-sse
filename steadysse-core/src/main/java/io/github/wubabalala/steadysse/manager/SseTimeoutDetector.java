package io.github.wubabalala.steadysse.manager;

import io.github.wubabalala.steadysse.config.SteadySseProperties;
import io.github.wubabalala.steadysse.spi.TimeoutProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Detects timed-out SSE connections and triggers cleanup.
 * <p>
 * Three-tier timeout detection:
 * <ol>
 *   <li><b>First-chunk timeout:</b> time between execution start and first data chunk</li>
 *   <li><b>Idle timeout:</b> time since last activity (after first chunk received)</li>
 *   <li><b>Hard timeout:</b> absolute maximum connection lifetime</li>
 * </ol>
 * <p>
 * Execution start is deliberately separated from registration time to exclude
 * queue wait time from the first-chunk timeout calculation.
 */
public class SseTimeoutDetector {

    private static final Logger log = LoggerFactory.getLogger(SseTimeoutDetector.class);

    private final SseConnectionManager connectionManager;
    private final SteadySseProperties properties;
    private final TimeoutProvider timeoutProvider; // nullable

    public SseTimeoutDetector(SseConnectionManager connectionManager,
                              SteadySseProperties properties,
                              TimeoutProvider timeoutProvider) {
        this.connectionManager = connectionManager;
        this.properties = properties;
        this.timeoutProvider = timeoutProvider;
    }

    /**
     * Check all active connections for timeouts. Called periodically by scheduler.
     */
    public void checkTimeouts() {
        Instant now = Instant.now();

        connectionManager.getConnections().forEach((key, wrapper) -> {
            try {
                if (wrapper.emitter.isFinalCompleted()) {
                    return; // already done
                }

                // Hard timeout (absolute maximum)
                Duration hardTimeout = getHardTimeout(key);
                if (Duration.between(wrapper.registrationTime, now).compareTo(hardTimeout) > 0) {
                    log.warn("[SteadySSE] Hard timeout: key={}, registered={}",
                            key, wrapper.registrationTime);
                    wrapper.emitter.completeWithTimeout("hard-timeout");
                    return;
                }

                // First-chunk timeout (only if execution has started but no data received yet)
                if (wrapper.executionStartTime != null && !wrapper.firstChunkReceived) {
                    Duration firstChunkTimeout = getFirstChunkTimeout(key);
                    if (Duration.between(wrapper.executionStartTime, now).compareTo(firstChunkTimeout) > 0) {
                        log.warn("[SteadySSE] First-chunk timeout: key={}, executionStart={}",
                                key, wrapper.executionStartTime);
                        wrapper.emitter.completeWithTimeout("first-chunk-timeout");
                        return;
                    }
                }

                // Idle timeout (only after first chunk received)
                if (wrapper.firstChunkReceived) {
                    Duration idleTimeout = getIdleTimeout(key);
                    if (Duration.between(wrapper.lastActivityTime, now).compareTo(idleTimeout) > 0) {
                        log.warn("[SteadySSE] Idle timeout: key={}, lastActivity={}",
                                key, wrapper.lastActivityTime);
                        wrapper.emitter.completeWithTimeout("idle-timeout");
                    }
                }
            } catch (Exception e) {
                log.error("[SteadySSE] Error checking timeout for key={}: {}", key, e.getMessage());
            }
        });
    }

    private Duration getFirstChunkTimeout(String key) {
        if (timeoutProvider != null) {
            return timeoutProvider.getFirstChunkTimeout(key);
        }
        return properties.getTimeout().getFirstChunk();
    }

    private Duration getIdleTimeout(String key) {
        if (timeoutProvider != null) {
            return timeoutProvider.getIdleTimeout(key);
        }
        return properties.getTimeout().getIdle();
    }

    private Duration getHardTimeout(String key) {
        if (timeoutProvider != null) {
            return timeoutProvider.getHardTimeout(key);
        }
        return properties.getTimeout().getHard();
    }
}
