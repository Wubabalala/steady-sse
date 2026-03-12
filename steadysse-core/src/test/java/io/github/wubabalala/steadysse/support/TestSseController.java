package io.github.wubabalala.steadysse.support;

import io.github.wubabalala.steadysse.emitter.FlushingSseEmitter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Test-only controller providing SSE endpoints for integration tests.
 * Used by FlushingSseEmitterIntegrationTest, SseHeartbeatDetectorIntegrationTest,
 * and SseAcceptanceTest.
 */
@RestController
@RequestMapping("/test")
public class TestSseController {

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
     * Long-running stream that stays open for 30s without sending data.
     * Used for heartbeat and timeout testing.
     */
    @GetMapping(value = "/long-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter longStream(HttpServletResponse response) {
        var emitter = new FlushingSseEmitter(60_000L, response);
        new Thread(() -> {
            try {
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
}
