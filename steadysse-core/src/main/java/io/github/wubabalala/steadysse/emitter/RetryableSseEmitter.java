package io.github.wubabalala.steadysse.emitter;

import io.github.wubabalala.steadysse.exception.SseExceptionClassifier;
import io.github.wubabalala.steadysse.lifecycle.CompositeSseLifecycleListener;
import io.github.wubabalala.steadysse.lifecycle.SseLifecycleListener;
import io.github.wubabalala.steadysse.lifecycle.StreamEndStatus;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * An SseEmitter with retry state machine and lifecycle management.
 * <p>
 * Core responsibilities:
 * <ol>
 *   <li>Intercept {@code complete()}/{@code completeWithError()} to support "fake completion" during retry</li>
 *   <li>Manage the retry state machine (retry mode, retry count, max retries)</li>
 *   <li>Provide lifecycle listener support via {@link CompositeSseLifecycleListener}</li>
 *   <li>Guard against Spring's setter-semantics on {@code onCompletion/onTimeout/onError}</li>
 * </ol>
 * <p>
 * <b>CRITICAL DESIGN RULE: Spring Callback Sole Entry Point</b>
 * <p>
 * This class is the ONLY place in the entire library that calls
 * {@code super.onCompletion()}, {@code super.onTimeout()}, {@code super.onError()}.
 * All other components MUST use {@link #addLifecycleListener(SseLifecycleListener)} instead.
 * This prevents Spring's setter-semantics from overwriting callbacks.
 *
 * @see SseLifecycleListener
 * @see CompositeSseLifecycleListener
 */
public class RetryableSseEmitter extends FlushingSseEmitter {

    private static final Logger log = LoggerFactory.getLogger(RetryableSseEmitter.class);

    // ========== State Management ==========

    private final AtomicBoolean retryMode = new AtomicBoolean(false);
    private final AtomicBoolean finalCompleted = new AtomicBoolean(false);
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private static final int MAX_RETRIES = 5;

    // ========== Lifecycle ==========

    private final CompositeSseLifecycleListener lifecycleListeners = new CompositeSseLifecycleListener();

    // ========== Callbacks ==========

    private RetryDecisionCallback retryCallback;
    private Runnable activityCallback;
    private String managerKey;

    /**
     * Callback interface for retry decisions.
     */
    @FunctionalInterface
    public interface RetryDecisionCallback {
        boolean shouldRetry(Throwable error);
    }

    // ========== Constructor ==========

    public RetryableSseEmitter(long timeout, HttpServletResponse response) {
        super(timeout, response);

        // === SOLE ENTRY POINT: Spring native callbacks → internal lifecycle ===
        // These are the ONLY calls to super.onCompletion/onTimeout/onError in the entire library.
        super.onCompletion(this::handleSpringOnCompletion);
        super.onTimeout(this::handleSpringOnTimeout);
        super.onError(this::handleSpringOnError);

        log.debug("[SteadySSE] RetryableSseEmitter created - timeout={}ms", timeout);
    }

    // ========== Override Guards ==========
    // Prevent external code from overwriting Spring callbacks.
    // All lifecycle hooks must go through addLifecycleListener().

    @Override
    public void onCompletion(Runnable callback) {
        throw new UnsupportedOperationException(
                "Direct onCompletion() is not allowed on RetryableSseEmitter. " +
                "Use addLifecycleListener() instead.");
    }

    @Override
    public void onTimeout(Runnable callback) {
        throw new UnsupportedOperationException(
                "Direct onTimeout() is not allowed on RetryableSseEmitter. " +
                "Use addLifecycleListener() instead.");
    }

    @Override
    public void onError(Consumer<Throwable> callback) {
        throw new UnsupportedOperationException(
                "Direct onError() is not allowed on RetryableSseEmitter. " +
                "Use addLifecycleListener() instead.");
    }

    // ========== Lifecycle Listener ==========

    /**
     * Add a lifecycle listener. This is the ONLY way to receive lifecycle events.
     * Multiple listeners can be added — they are never overwritten (unlike Spring's setter semantics).
     */
    public void addLifecycleListener(SseLifecycleListener listener) {
        lifecycleListeners.add(listener);
    }

    // ========== Spring Callback Handlers ==========

    private void handleSpringOnCompletion() {
        // Spring onCompletion fires after response is committed.
        // If we haven't already finalized, treat it as a success completion.
        if (!finalCompleted.get()) {
            log.debug("[SteadySSE] Spring onCompletion triggered (fallback path)");
            doFinalComplete(StreamEndStatus.SUCCESS, null, null);
        }
    }

    private void handleSpringOnTimeout() {
        log.debug("[SteadySSE] Spring onTimeout triggered (fallback path)");
        doFinalComplete(StreamEndStatus.TIMEOUT, null, "spring-timeout");
    }

    private void handleSpringOnError(Throwable ex) {
        log.debug("[SteadySSE] Spring onError triggered (fallback path): {}", ex.getMessage());
        doFinalComplete(StreamEndStatus.ERROR, ex, null);
    }

    // ========== Core Methods ==========

    /**
     * Central finalization logic. All completeXxx methods funnel through here.
     * CAS ensures this runs exactly once.
     * <p>
     * <b>Signal ordering contract:</b>
     * <ol>
     *   <li>Detail signal first: {@code onTimeout(reason)} / {@code onCancel()} / {@code onError(error)}</li>
     *   <li>Terminal signal last: {@code onComplete(status)} — always fires exactly once</li>
     * </ol>
     * <p>
     * SseConnectionManager (and all external listeners) should do cleanup ONLY in
     * {@code onComplete}, which is guaranteed to fire exactly once for any exit path.
     * Detail signals ({@code onTimeout/onCancel/onError}) provide additional context
     * but must NOT duplicate cleanup logic.
     *
     * @param status the end status
     * @param error the error (null for non-error paths)
     * @param timeoutReason the timeout reason (null for non-timeout paths)
     */
    private boolean doFinalComplete(StreamEndStatus status, Throwable error, String timeoutReason) {
        if (!finalCompleted.compareAndSet(false, true)) {
            return false;
        }

        retryMode.set(false);

        // 1. Detail signals first (provide context to listeners)
        switch (status) {
            case SUCCESS -> { /* no detail signal */ }
            case TIMEOUT -> {
                if (timeoutReason != null) {
                    lifecycleListeners.fireTimeout(timeoutReason);
                }
            }
            case CANCELLED -> lifecycleListeners.fireCancel();
            case ERROR -> {
                if (error != null) {
                    lifecycleListeners.fireError(error);
                }
            }
        }

        // 2. Terminal signal last — exactly once, covers all paths
        //    Listeners should do cleanup here (semaphore release, connection removal, etc.)
        lifecycleListeners.fireComplete(status);

        // 3. Close the connection
        try {
            super.complete();
        } catch (Exception e) {
            log.trace("[SteadySSE] super.complete() failed (client likely disconnected): {}", e.getMessage());
        }

        return true;
    }

    /**
     * Intercepted: in retry mode, complete() is suppressed to keep the connection alive.
     */
    @Override
    public void complete() {
        if (retryMode.get()) {
            log.debug("[SteadySSE] Intercepting complete() — retry mode active, keeping connection alive");
            return;
        }

        if (doFinalComplete(StreamEndStatus.SUCCESS, null, null)) {
            log.debug("[SteadySSE] Normal completion - retryCount={}", retryCount.get());
        }
    }

    /**
     * Intercepted: checks retry eligibility before deciding whether to retry or finalize.
     * <p>
     * Protection chain:
     * <ol>
     *   <li>Max retries exceeded → finalize</li>
     *   <li>Client disconnected → finalize (no point retrying)</li>
     *   <li>Already cancelled → finalize</li>
     *   <li>Retry callback says yes → enter retry mode</li>
     *   <li>Otherwise → finalize</li>
     * </ol>
     */
    @Override
    public void completeWithError(Throwable ex) {
        if (retryCount.get() >= MAX_RETRIES) {
            log.error("[SteadySSE] Max retries reached ({}), forcing completion - error={}",
                    MAX_RETRIES, ex.getMessage());
            finalComplete(ex);
            return;
        }

        if (SseExceptionClassifier.isClientDisconnect(ex)) {
            log.debug("[SteadySSE] Client disconnected, not retrying - error={}", ex.getMessage());
            finalComplete(ex);
            return;
        }

        if (cancelled.get()) {
            log.info("[SteadySSE] Connection cancelled, not retrying - error={}", ex.getMessage());
            finalComplete(ex);
            return;
        }

        if (retryCallback != null && retryCallback.shouldRetry(ex)) {
            int currentRetry = retryCount.incrementAndGet();
            log.info("[SteadySSE] Entering retry mode - retryCount={}/{}, error={}",
                    currentRetry, MAX_RETRIES, ex.getMessage());
            retryMode.set(true);
            return;
        }

        log.warn("[SteadySSE] Not retryable, finalizing - error={}", ex.getMessage());
        finalComplete(ex);
    }

    /**
     * Exit retry mode. Called by the upstream provider manager after a retry attempt starts.
     */
    public void exitRetryMode() {
        if (retryMode.compareAndSet(true, false)) {
            log.info("[SteadySSE] Exited retry mode - totalRetries={}", retryCount.get());
        }
    }

    /**
     * Force final completion (success or error).
     */
    public void finalComplete(Throwable error) {
        if (error == null) {
            doFinalComplete(StreamEndStatus.SUCCESS, null, null);
        } else {
            doFinalComplete(StreamEndStatus.ERROR, error, null);
        }
    }

    /**
     * Complete with timeout status.
     */
    public void completeWithTimeout(String reason) {
        if (doFinalComplete(StreamEndStatus.TIMEOUT, null, reason)) {
            log.info("[SteadySSE] Timeout completion - reason={}", reason);
        }
    }

    /**
     * Complete with cancellation status.
     */
    public void completeWithCancel() {
        cancelled.set(true);
        if (doFinalComplete(StreamEndStatus.CANCELLED, null, null)) {
            log.info("[SteadySSE] Cancel completion - retryCount={}", retryCount.get());
        }
    }

    // ========== Send Methods ==========

    @Override
    public void send(Object object) throws IOException {
        super.send(object);
        triggerActivityCallback();
    }

    @Override
    public void send(Object object, MediaType mediaType) throws IOException {
        super.send(object, mediaType);
        triggerActivityCallback();
    }

    @Override
    public void send(SseEventBuilder builder) throws IOException {
        super.send(builder);
        triggerActivityCallback();
    }

    /**
     * Send a heartbeat (SSE comment frame). Does NOT trigger activity callback,
     * so it does not reset the idle timeout — this allows detecting truly stuck
     * connections vs slow-but-alive ones.
     */
    public void sendHeartbeat() throws IOException {
        super.send(SseEmitter.event().comment("heartbeat").build());
        // Intentionally NOT calling triggerActivityCallback()
    }

    private void triggerActivityCallback() {
        if (activityCallback != null) {
            try {
                activityCallback.run();
            } catch (Exception e) {
                log.trace("[SteadySSE] Activity callback failed: {}", e.getMessage());
            }
        }
    }

    // ========== Configuration ==========

    public void setRetryCallback(RetryDecisionCallback callback) {
        this.retryCallback = callback;
    }

    public void setActivityCallback(Runnable callback) {
        this.activityCallback = callback;
    }

    public void setManagerKey(String key) {
        this.managerKey = key;
    }

    public String getManagerKey() {
        return managerKey;
    }

    // ========== State Queries ==========

    public boolean isFinalCompleted() {
        return finalCompleted.get();
    }

    public boolean isCancelled() {
        return cancelled.get() || finalCompleted.get();
    }

    public void markCancelled() {
        if (cancelled.compareAndSet(false, true)) {
            log.info("[SteadySSE] Connection marked as cancelled - retryCount={}", retryCount.get());
        }
    }

    public int getRetryCount() {
        return retryCount.get();
    }

    public String getState() {
        return String.format("retryMode=%s, finalCompleted=%s, retryCount=%d/%d, cancelled=%s",
                retryMode.get(), finalCompleted.get(), retryCount.get(), MAX_RETRIES, cancelled.get());
    }
}
