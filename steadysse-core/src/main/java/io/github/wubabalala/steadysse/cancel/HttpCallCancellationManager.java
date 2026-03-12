package io.github.wubabalala.steadysse.cancel;

import io.github.wubabalala.steadysse.lifecycle.SseLifecycleListener;
import io.github.wubabalala.steadysse.lifecycle.StreamEndStatus;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages upstream HTTP call cancellation, linked to SSE connection lifecycle.
 * <p>
 * Key design: provides {@link #createLifecycleListenerFor(String)} which returns
 * an {@link SseLifecycleListener} bound to a specific connection key. When the SSE
 * connection ends (for any reason), the listener automatically cancels the associated
 * upstream HTTP call. This is the "lifecycle-linked auto-cancellation" mechanism.
 * <p>
 * Usage:
 * <pre>{@code
 * // Register the upstream call
 * cancellationManager.registerCall(key, new OkHttpCancellableCall(okHttpCall));
 *
 * // Bind to emitter lifecycle (auto-cancel on disconnect)
 * emitter.addLifecycleListener(cancellationManager.createLifecycleListenerFor(key));
 * }</pre>
 */
public class HttpCallCancellationManager {

    private static final Logger log = LoggerFactory.getLogger(HttpCallCancellationManager.class);

    private final ConcurrentHashMap<String, CancellableCall> activeCalls = new ConcurrentHashMap<>();

    /**
     * Register an upstream HTTP call associated with a connection key.
     */
    public void registerCall(String key, CancellableCall call) {
        activeCalls.put(Objects.requireNonNull(key), Objects.requireNonNull(call));
        log.debug("[SteadySSE] Registered upstream call for key={}", key);
    }

    /**
     * Cancel and remove the upstream call for the given key.
     */
    public void cancelByKey(String key, String reason) {
        CancellableCall call = activeCalls.remove(key);
        if (call != null) {
            log.info("[SteadySSE] Cancelling upstream call for key={}, reason={}", key, reason);
            call.cancel();
        }
    }

    /**
     * Remove a call registration without cancelling it.
     * Used when the call has already completed normally.
     */
    public void removeByKey(String key) {
        activeCalls.remove(key);
    }

    /**
     * Cancel all active upstream calls. Called on application shutdown.
     */
    public void cancelAll(String reason) {
        activeCalls.forEach((key, call) -> {
            log.info("[SteadySSE] Cancelling upstream call for key={}, reason={}", key, reason);
            call.cancel();
        });
        activeCalls.clear();
    }

    /**
     * Spring shutdown hook — must be no-arg per @PreDestroy contract.
     */
    @PreDestroy
    public void shutdown() {
        cancelAll("application-shutdown");
    }

    public int getActiveCallCount() {
        return activeCalls.size();
    }

    /**
     * Create an {@link SseLifecycleListener} bound to the given connection key.
     * When the SSE connection ends (complete, timeout, error, cancel), the listener
     * automatically cancels and removes the associated upstream HTTP call.
     * <p>
     * This is the core mechanism for "lifecycle-linked auto-cancellation".
     *
     * @param key the connection key (must match the key used in {@link #registerCall})
     * @return a lifecycle listener that auto-cancels the upstream call
     */
    public SseLifecycleListener createLifecycleListenerFor(String key) {
        return new SseLifecycleListener() {
            @Override
            public void onComplete(StreamEndStatus status) {
                cancelByKey(key, "stream-" + status);
            }

            @Override
            public void onTimeout(String reason) {
                cancelByKey(key, "timeout:" + reason);
            }

            @Override
            public void onError(Throwable error) {
                cancelByKey(key, "error:" + error.getMessage());
            }

            @Override
            public void onCancel() {
                cancelByKey(key, "user-cancel");
            }
        };
    }
}
