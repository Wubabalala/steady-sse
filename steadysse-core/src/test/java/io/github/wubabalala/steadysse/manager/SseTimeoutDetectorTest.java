package io.github.wubabalala.steadysse.manager;

import io.github.wubabalala.steadysse.config.SteadySseProperties;
import io.github.wubabalala.steadysse.emitter.RetryableSseEmitter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SseTimeoutDetectorTest {

    @Test
    void firstChunkTimeoutDetected() throws Exception {
        var props = new SteadySseProperties();
        props.setMaxConcurrent(10);
        props.getTimeout().setFirstChunk(Duration.ofMillis(100));
        props.getTimeout().setIdle(Duration.ofSeconds(60));
        props.getTimeout().setHard(Duration.ofSeconds(300));

        var manager = new SseConnectionManager(props);
        var detector = new SseTimeoutDetector(manager, props, null);
        var emitter = new RetryableSseEmitter(60_000L, new MockHttpServletResponse());
        manager.register("k1", emitter);
        manager.markExecutionStart("k1");

        Thread.sleep(200);
        detector.checkTimeouts();

        assertThat(emitter.isFinalCompleted()).isTrue();
    }

    @Test
    void executionStartNotMarkedNoFirstChunkTimeout() throws Exception {
        var props = new SteadySseProperties();
        props.setMaxConcurrent(10);
        props.getTimeout().setFirstChunk(Duration.ofMillis(100));

        var manager = new SseConnectionManager(props);
        var detector = new SseTimeoutDetector(manager, props, null);
        var emitter = new RetryableSseEmitter(60_000L, new MockHttpServletResponse());
        manager.register("k1", emitter);
        // NOT calling markExecutionStart — still queued

        Thread.sleep(200);
        detector.checkTimeouts();

        assertThat(emitter.isFinalCompleted()).isFalse();
    }

    @Test
    void idleTimeoutDetected() throws Exception {
        var props = new SteadySseProperties();
        props.setMaxConcurrent(10);
        props.getTimeout().setFirstChunk(Duration.ofSeconds(60));
        props.getTimeout().setIdle(Duration.ofMillis(100));
        props.getTimeout().setHard(Duration.ofSeconds(300));

        var manager = new SseConnectionManager(props);
        var detector = new SseTimeoutDetector(manager, props, null);
        var emitter = new RetryableSseEmitter(60_000L, new MockHttpServletResponse());
        manager.register("k1", emitter);
        manager.markExecutionStart("k1");
        manager.markFirstChunkReceived("k1");

        Thread.sleep(200);
        detector.checkTimeouts();

        assertThat(emitter.isFinalCompleted()).isTrue();
    }

    @Test
    void hardTimeoutDetected() throws Exception {
        var props = new SteadySseProperties();
        props.setMaxConcurrent(10);
        props.getTimeout().setFirstChunk(Duration.ofSeconds(60));
        props.getTimeout().setIdle(Duration.ofSeconds(60));
        props.getTimeout().setHard(Duration.ofMillis(100));

        var manager = new SseConnectionManager(props);
        var detector = new SseTimeoutDetector(manager, props, null);
        var emitter = new RetryableSseEmitter(60_000L, new MockHttpServletResponse());
        manager.register("k1", emitter);

        Thread.sleep(200);
        detector.checkTimeouts();

        assertThat(emitter.isFinalCompleted()).isTrue();
    }

    @Test
    void recentActivityPreventsIdleTimeout() throws Exception {
        var props = new SteadySseProperties();
        props.setMaxConcurrent(10);
        props.getTimeout().setIdle(Duration.ofMillis(300));
        props.getTimeout().setHard(Duration.ofSeconds(300));

        var manager = new SseConnectionManager(props);
        var detector = new SseTimeoutDetector(manager, props, null);
        var emitter = new RetryableSseEmitter(60_000L, new MockHttpServletResponse());
        manager.register("k1", emitter);
        manager.markExecutionStart("k1");
        manager.markFirstChunkReceived("k1");

        Thread.sleep(100);
        manager.updateActivity("k1"); // refresh activity
        Thread.sleep(100);
        detector.checkTimeouts();

        // Should NOT be timed out (200ms elapsed, but activity was at 100ms, so idle = 100ms < 300ms)
        assertThat(emitter.isFinalCompleted()).isFalse();
    }

    @Test
    void alreadyCompletedEmitterSkipped() {
        var props = new SteadySseProperties();
        props.setMaxConcurrent(10);
        props.getTimeout().setHard(Duration.ofMillis(1)); // instant timeout

        var manager = new SseConnectionManager(props);
        var detector = new SseTimeoutDetector(manager, props, null);
        var emitter = new RetryableSseEmitter(60_000L, new MockHttpServletResponse());
        manager.register("k1", emitter);

        emitter.complete(); // already done

        // Should not throw or double-complete
        detector.checkTimeouts();
    }
}
