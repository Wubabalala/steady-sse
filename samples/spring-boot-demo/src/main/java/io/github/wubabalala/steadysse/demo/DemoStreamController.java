package io.github.wubabalala.steadysse.demo;

import io.github.wubabalala.steadysse.cancel.HttpCallCancellationManager;
import io.github.wubabalala.steadysse.emitter.RetryableSseEmitter;
import io.github.wubabalala.steadysse.manager.SseConnectionManager;
import io.github.wubabalala.steadysse.protocol.SseEvents;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Demo controller showing how to use SteadySSE in a real application.
 */
@RestController
public class DemoStreamController {

    private final SseConnectionManager connectionManager;
    private final HttpCallCancellationManager cancellationManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public DemoStreamController(SseConnectionManager connectionManager,
                                 HttpCallCancellationManager cancellationManager) {
        this.connectionManager = connectionManager;
        this.cancellationManager = cancellationManager;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse response) {
        var emitter = new RetryableSseEmitter(60_000L, response);
        String key = "demo:" + UUID.randomUUID();

        connectionManager.register(key, emitter);
        connectionManager.markExecutionStart(key);

        executor.submit(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    if (emitter.isCancelled()) {
                        break;
                    }

                    if (i == 1) {
                        connectionManager.markFirstChunkReceived(key);
                    }

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
}
