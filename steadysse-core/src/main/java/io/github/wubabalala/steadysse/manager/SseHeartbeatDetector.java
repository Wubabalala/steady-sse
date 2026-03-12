package io.github.wubabalala.steadysse.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
 * <p>
 * <b>Parallelism:</b> Heartbeats are sent concurrently to avoid a slow socket
 * blocking all other connections. On Java 21+, virtual threads are used automatically.
 * On Java 17-20, a cached thread pool provides bounded parallelism.
 */
public class SseHeartbeatDetector {

    private static final Logger log = LoggerFactory.getLogger(SseHeartbeatDetector.class);

    private final SseConnectionManager connectionManager;
    private final ExecutorService heartbeatExecutor;

    public SseHeartbeatDetector(SseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.heartbeatExecutor = createHeartbeatExecutor();
    }

    /**
     * Send heartbeats to all active connections in parallel.
     * <p>
     * Each connection gets its own task so a slow/dead socket doesn't block others.
     * Waits for all heartbeats to complete before returning (bounded by socket timeout).
     */
    public void sendHeartbeats() {
        var connections = connectionManager.getConnections();
        if (connections.isEmpty()) {
            return;
        }

        var futures = new ArrayList<CompletableFuture<Void>>(connections.size());

        connections.forEach((key, wrapper) -> {
            futures.add(CompletableFuture.runAsync(() -> sendSingleHeartbeat(key, wrapper), heartbeatExecutor));
        });

        // Wait for all heartbeats to finish
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private void sendSingleHeartbeat(String key, SseConnectionManager.EmitterWrapper wrapper) {
        try {
            if (wrapper.emitter.isFinalCompleted()) {
                return;
            }
            wrapper.emitter.sendHeartbeat();
        } catch (Exception e) {
            log.debug("[SteadySSE] Heartbeat failed for key={} (client likely disconnected): {}",
                    key, e.getMessage());
            try {
                wrapper.emitter.markCancelled();
                wrapper.emitter.completeWithError(
                        new java.io.IOException("Client disconnected (heartbeat failed): " + e.getMessage()));
            } catch (Exception cleanupEx) {
                log.trace("[SteadySSE] Cleanup after heartbeat failure also failed for key={}: {}",
                        key, cleanupEx.getMessage());
                connectionManager.removeOnly(key);
            }
        }
    }

    /**
     * Create the executor for parallel heartbeat sending.
     * Uses virtual threads on Java 21+, falls back to cached thread pool on Java 17-20.
     */
    private static ExecutorService createHeartbeatExecutor() {
        try {
            // Java 21+: virtual threads — ideal for I/O-bound heartbeat writes
            var method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            var executor = (ExecutorService) method.invoke(null);
            log.info("[SteadySSE] Heartbeat executor: virtual threads (Java 21+)");
            return executor;
        } catch (Exception e) {
            // Java 17-20: cached thread pool with daemon threads
            log.info("[SteadySSE] Heartbeat executor: cached thread pool (Java <21)");
            return Executors.newCachedThreadPool(r -> {
                var t = new Thread(r, "steadysse-heartbeat");
                t.setDaemon(true);
                return t;
            });
        }
    }
}
