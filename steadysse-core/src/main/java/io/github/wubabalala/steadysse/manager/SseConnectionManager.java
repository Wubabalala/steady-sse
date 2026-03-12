package io.github.wubabalala.steadysse.manager;

import io.github.wubabalala.steadysse.config.SteadySseProperties;
import io.github.wubabalala.steadysse.emitter.RetryableSseEmitter;
import io.github.wubabalala.steadysse.exception.SseConnectionRejectedException;
import io.github.wubabalala.steadysse.lifecycle.SseLifecycleListener;
import io.github.wubabalala.steadysse.lifecycle.StreamEndStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Manages active SSE connections with concurrency control and lifecycle tracking.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Registry: tracks all active connections by key</li>
 *   <li>Concurrency control: Semaphore-based connection limiting</li>
 *   <li>Lifecycle binding: auto-registers a cleanup listener on each emitter</li>
 *   <li>Activity tracking: records execution start and last activity time</li>
 * </ul>
 * <p>
 * <b>Cleanup contract:</b> cleanup (map removal + semaphore release) happens ONLY in
 * the lifecycle listener's {@code onComplete}, which is the terminal signal guaranteed
 * to fire exactly once for any exit path. This prevents duplicate cleanup.
 *
 * @see SseLifecycleListener
 * @see RetryableSseEmitter
 */
public class SseConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(SseConnectionManager.class);

    private final ConcurrentHashMap<String, EmitterWrapper> connections = new ConcurrentHashMap<>();
    private final Semaphore semaphore;
    private final int maxConcurrent;

    public SseConnectionManager(SteadySseProperties properties) {
        this.maxConcurrent = properties.getMaxConcurrent();
        this.semaphore = new Semaphore(maxConcurrent);
    }

    /**
     * Register a new SSE connection with concurrency control.
     * <p>
     * Automatically adds a lifecycle listener that cleans up the connection
     * and releases the semaphore when the emitter completes (for any reason).
     *
     * @throws SseConnectionRejectedException if concurrency limit is reached
     */
    public void register(String key, RetryableSseEmitter emitter) {
        if (!semaphore.tryAcquire()) {
            throw new SseConnectionRejectedException(
                    "SSE connection limit reached (" + maxConcurrent + "). Try again later.");
        }

        var wrapper = new EmitterWrapper(emitter);
        connections.put(key, wrapper);

        // Bind activity callback — refreshes idle timeout on each successful send
        emitter.setActivityCallback(() -> updateActivity(key));

        // Auto-register cleanup listener — cleanup ONLY in onComplete (terminal signal)
        emitter.addLifecycleListener(new SseLifecycleListener() {
            @Override
            public void onComplete(StreamEndStatus status) {
                doRelease(key, "lifecycle-" + status);
            }
        });

        log.debug("[SteadySSE] Connection registered: key={}, active={}/{}",
                key, getActiveCount(), maxConcurrent);
    }

    /**
     * Release semaphore and remove connection. Idempotent — safe to call multiple times.
     */
    private void doRelease(String key, String reason) {
        EmitterWrapper removed = connections.remove(key);
        if (removed != null) {
            if (removed.permitAcquired) {
                removed.permitAcquired = false;
                semaphore.release();
            }
            log.debug("[SteadySSE] Connection released: key={}, reason={}, active={}/{}",
                    key, reason, getActiveCount(), maxConcurrent);
        }
    }

    /**
     * Remove and release by key. Triggers emitter completion with cancel status.
     */
    public void releaseAndRemove(String key) {
        EmitterWrapper wrapper = connections.get(key);
        if (wrapper != null) {
            wrapper.emitter.completeWithCancel();
            // doRelease will be called by the lifecycle listener
        }
    }

    /**
     * Remove connection from registry without calling complete on the emitter.
     * Used during retry mode when the emitter should stay alive.
     */
    public void removeOnly(String key) {
        doRelease(key, "removeOnly");
    }

    /**
     * Cancel all connections matching the given key prefix.
     */
    public void cancelByPrefix(String prefix) {
        connections.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getKey)
                .toList() // snapshot to avoid ConcurrentModificationException
                .forEach(key -> {
                    EmitterWrapper wrapper = connections.get(key);
                    if (wrapper != null) {
                        wrapper.emitter.markCancelled();
                        wrapper.emitter.completeWithCancel();
                    }
                });
    }

    /**
     * Mark that execution has started (queue wait is over).
     * First-chunk timeout is measured from this point, not from registration.
     */
    public void markExecutionStart(String key) {
        EmitterWrapper wrapper = connections.get(key);
        if (wrapper != null) {
            wrapper.executionStartTime = Instant.now();
        }
    }

    /**
     * Mark that the first chunk has been received.
     * Switches from first-chunk timeout to idle timeout tracking.
     */
    public void markFirstChunkReceived(String key) {
        EmitterWrapper wrapper = connections.get(key);
        if (wrapper != null) {
            wrapper.firstChunkReceived = true;
            wrapper.lastActivityTime = Instant.now();
        }
    }

    /**
     * Update last activity time (called on each successful send).
     */
    public void updateActivity(String key) {
        EmitterWrapper wrapper = connections.get(key);
        if (wrapper != null) {
            wrapper.lastActivityTime = Instant.now();
        }
    }

    public int getActiveCount() {
        return connections.size();
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public int getAvailablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * Package-private access to connection registry for SseTimeoutDetector and SseHeartbeatDetector.
     */
    ConcurrentHashMap<String, EmitterWrapper> getConnections() {
        return connections;
    }

    /**
     * Internal wrapper tracking connection state.
     */
    static class EmitterWrapper {
        final RetryableSseEmitter emitter;
        final Instant registrationTime;
        volatile Instant executionStartTime;
        volatile Instant lastActivityTime;
        volatile boolean firstChunkReceived;
        volatile boolean permitAcquired;

        EmitterWrapper(RetryableSseEmitter emitter) {
            this.emitter = emitter;
            this.registrationTime = Instant.now();
            this.lastActivityTime = Instant.now();
            this.permitAcquired = true;
        }
    }
}
