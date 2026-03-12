package io.github.wubabalala.steadysse.support;

import io.github.wubabalala.steadysse.cancel.HttpCallCancellationManager;
import io.github.wubabalala.steadysse.emitter.FlushingSseEmitter;
import io.github.wubabalala.steadysse.emitter.RetryableSseEmitter;
import io.github.wubabalala.steadysse.manager.SseConnectionManager;
import io.github.wubabalala.steadysse.protocol.SseEvents;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test-only controller providing SSE endpoints for integration tests.
 */
@RestController
@RequestMapping("/test")
public class TestSseController {

    @Autowired
    private SseConnectionManager connectionManager;

    @Autowired
    private HttpCallCancellationManager cancellationManager;

    private final AtomicReference<FakeCancellableCall> lastFakeCall = new AtomicReference<>();

    /**
     * Sends 5 chunks with 200ms intervals. Used to verify flush penetration.
     */
    @GetMapping(value = "/flush-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter flushStream(HttpServletResponse response) {
        var emitter = new FlushingSseEmitter(60_000L, response);
        new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    emitter.send(SseEmitter.event().data("chunk-" + i + ":" + System.currentTimeMillis()));
                    Thread.sleep(200);
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }

    /**
     * Managed stream with 5 chunks × 200ms. Uses SseConnectionManager for lifecycle tracking.
     */
    @GetMapping(value = "/managed-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter managedStream(HttpServletResponse response) {
        var emitter = new RetryableSseEmitter(60_000L, response);
        String key = "test:" + UUID.randomUUID();
        connectionManager.register(key, emitter);
        connectionManager.markExecutionStart(key);

        new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    if (emitter.isCancelled()) break;
                    if (i == 0) connectionManager.markFirstChunkReceived(key);
                    emitter.send(SseEvents.chunk("chunk-" + i));
                    Thread.sleep(200);
                }
                emitter.send(SseEvents.done());
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }

    /**
     * Long-running stream that goes idle after initial setup.
     * Sends one comment to establish the HTTP response, then hangs.
     * Used for timeout testing (first-chunk timeout since no real data is sent).
     */
    @GetMapping(value = "/long-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter longStream(HttpServletResponse response) {
        var emitter = new RetryableSseEmitter(60_000L, response);
        String key = "long:" + UUID.randomUUID();
        connectionManager.register(key, emitter);
        connectionManager.markExecutionStart(key);
        // Note: NOT calling markFirstChunkReceived — this is a first-chunk timeout test

        new Thread(() -> {
            try {
                // Send a comment to establish the HTTP response (so OkHttp execute() returns)
                emitter.sendHeartbeat();
                Thread.sleep(30_000);
                emitter.complete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (!emitter.isFinalCompleted()) {
                    emitter.completeWithError(e);
                }
            }
        }).start();
        return emitter;
    }

    /**
     * Stream that registers a FakeCancellableCall for auto-cancellation testing.
     * Sends slowly and never completes — expects client disconnect to trigger cleanup.
     */
    @GetMapping(value = "/cancel-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter cancelStream(HttpServletResponse response) {
        var emitter = new RetryableSseEmitter(60_000L, response);
        String key = "cancel:" + UUID.randomUUID();
        connectionManager.register(key, emitter);
        connectionManager.markExecutionStart(key);

        // Register a FakeCancellableCall and link it to this connection's lifecycle
        var fakeCall = new FakeCancellableCall();
        lastFakeCall.set(fakeCall);
        cancellationManager.registerCall(key, fakeCall);
        emitter.addLifecycleListener(cancellationManager.createLifecycleListenerFor(key));

        new Thread(() -> {
            try {
                // Send one chunk to establish connection, then hang
                emitter.send(SseEvents.chunk("initial"));
                connectionManager.markFirstChunkReceived(key);
                Thread.sleep(30_000);
                emitter.complete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }

    /**
     * Simple concurrent stream for testing concurrency limits.
     */
    @GetMapping(value = "/concurrent-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter concurrentStream(HttpServletResponse response) {
        var emitter = new RetryableSseEmitter(60_000L, response);
        String key = "concurrent:" + UUID.randomUUID();
        connectionManager.register(key, emitter);

        new Thread(() -> {
            try {
                Thread.sleep(10_000);
                emitter.complete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }

    public FakeCancellableCall getLastFakeCall() {
        return lastFakeCall.get();
    }
}
