package io.github.wubabalala.steadysse.demo;

import io.github.wubabalala.steadysse.cancel.CancellableCall;
import io.github.wubabalala.steadysse.cancel.HttpCallCancellationManager;
import io.github.wubabalala.steadysse.emitter.RetryableSseEmitter;
import io.github.wubabalala.steadysse.manager.SseConnectionManager;
import io.github.wubabalala.steadysse.protocol.SseEvents;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Demo controller showing how to use SteadySSE in a real application.
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code /stream} — basic SSE stream with 10 chunks</li>
 *   <li>{@code /stream-with-cancel} — demonstrates upstream call auto-cancellation on disconnect</li>
 * </ul>
 */
@RestController
public class DemoStreamController {

    private static final Logger log = LoggerFactory.getLogger(DemoStreamController.class);

    private final SseConnectionManager connectionManager;
    private final HttpCallCancellationManager cancellationManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public DemoStreamController(SseConnectionManager connectionManager,
                                 HttpCallCancellationManager cancellationManager) {
        this.connectionManager = connectionManager;
        this.cancellationManager = cancellationManager;
    }

    /**
     * Basic SSE stream — 10 chunks at 500ms intervals.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse response) {
        var emitter = new RetryableSseEmitter(60_000L, response);
        String key = "demo:" + UUID.randomUUID();

        connectionManager.register(key, emitter);
        connectionManager.markExecutionStart(key);

        executor.submit(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    if (emitter.isCancelled()) break;
                    if (i == 1) connectionManager.markFirstChunkReceived(key);
                    emitter.send(SseEvents.chunk("Hello chunk " + i + "\n"));
                    Thread.sleep(500);
                }
                emitter.send(SseEvents.done());
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * SSE stream with upstream call auto-cancellation.
     * <p>
     * Demonstrates the lifecycle-linked cancellation pattern:
     * if the client disconnects mid-stream, the simulated upstream call
     * is automatically cancelled via the lifecycle listener.
     */
    @GetMapping(value = "/stream-with-cancel", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWithCancel(HttpServletResponse response) {
        var emitter = new RetryableSseEmitter(60_000L, response);
        String key = "cancel-demo:" + UUID.randomUUID();

        connectionManager.register(key, emitter);
        connectionManager.markExecutionStart(key);

        // Simulate an upstream HTTP call that can be cancelled
        var simulatedCall = new SimulatedUpstreamCall();
        cancellationManager.registerCall(key, simulatedCall);

        // Bind lifecycle listener — auto-cancels the upstream call on disconnect
        emitter.addLifecycleListener(cancellationManager.createLifecycleListenerFor(key));

        executor.submit(() -> {
            try {
                for (int i = 1; i <= 20; i++) {
                    if (emitter.isCancelled() || simulatedCall.isCancelled()) {
                        log.info("[Demo] Upstream call was cancelled at chunk {}", i);
                        break;
                    }
                    if (i == 1) connectionManager.markFirstChunkReceived(key);
                    emitter.send(SseEvents.chunk("Chunk " + i + " (close tab to see cancel)\n"));
                    Thread.sleep(1000);
                }

                if (!simulatedCall.isCancelled()) {
                    // Normal completion — remove the call registration
                    cancellationManager.removeByKey(key);
                    emitter.send(SseEvents.done());
                    emitter.complete();
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Simulated upstream call for demo purposes.
     * In real applications, use {@link io.github.wubabalala.steadysse.cancel.OkHttpCancellableCall}
     * to wrap actual OkHttp calls.
     */
    private static class SimulatedUpstreamCall implements CancellableCall {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        @Override
        public void cancel() {
            cancelled.set(true);
            log.info("[Demo] Simulated upstream call CANCELLED — this is the auto-cancellation in action");
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
