package io.github.wubabalala.steadysse.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends SSE comment-frame heartbeats to all active connections.
 * <p>
 * Heartbeats serve two purposes:
 * <ol>
 *   <li>Keep-alive: prevents proxies and load balancers from closing idle connections</li>
 *   <li>Disconnect detection: if send fails, the connection is dead and should be cleaned up</li>
 * </ol>
 * <p>
 * <b>Important:</b> Heartbeats intentionally do NOT update the activity time.
 * This allows the idle timeout detector to correctly identify truly stuck connections
 * (no real data flowing) vs slow-but-alive connections.
 */
public class SseHeartbeatDetector {

    private static final Logger log = LoggerFactory.getLogger(SseHeartbeatDetector.class);

    private final SseConnectionManager connectionManager;

    public SseHeartbeatDetector(SseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Send heartbeats to all active connections. Called periodically by scheduler.
     * <p>
     * If a heartbeat send fails (client disconnected), triggers emitter completion
     * which cascades through the lifecycle listener to clean up the connection.
     */
    public void sendHeartbeats() {
        connectionManager.getConnections().forEach((key, wrapper) -> {
            try {
                if (wrapper.emitter.isFinalCompleted()) {
                    return;
                }
                wrapper.emitter.sendHeartbeat();
            } catch (Exception e) {
                log.debug("[SteadySSE] Heartbeat failed for key={} (client likely disconnected): {}",
                        key, e.getMessage());
                try {
                    // Mark cancelled first to prevent retry attempts on a dead connection
                    wrapper.emitter.markCancelled();
                    // Use completeWithError — this is a disconnect, not a timeout
                    wrapper.emitter.completeWithError(
                            new java.io.IOException("Client disconnected (heartbeat failed): " + e.getMessage()));
                } catch (Exception cleanupEx) {
                    log.trace("[SteadySSE] Cleanup after heartbeat failure also failed for key={}: {}",
                            key, cleanupEx.getMessage());
                    connectionManager.removeOnly(key);
                }
            }
        });
    }
}
