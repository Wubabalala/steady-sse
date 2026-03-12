package io.github.wubabalala.steadysse.manager;

import io.github.wubabalala.steadysse.config.SteadySseProperties;
import io.github.wubabalala.steadysse.emitter.RetryableSseEmitter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SseHeartbeatDetectorTest {

    @Test
    void heartbeatSucceedsInMockEnvironment() {
        // MockHttpServletResponse silently accepts data — heartbeat "succeeds"
        // Real disconnect detection is tested in integration tests (Task 14)
        var props = new SteadySseProperties();
        props.setMaxConcurrent(10);
        var manager = new SseConnectionManager(props);
        var detector = new SseHeartbeatDetector(manager);
        var emitter = new RetryableSseEmitter(60_000L, new MockHttpServletResponse());
        manager.register("k1", emitter);

        detector.sendHeartbeats();

        // In mock environment, heartbeat succeeds → connection stays active
        assertThat(manager.getActiveCount()).isEqualTo(1);
    }

    @Test
    void alreadyCompletedEmitterSkipped() {
        var props = new SteadySseProperties();
        props.setMaxConcurrent(10);
        var manager = new SseConnectionManager(props);
        var detector = new SseHeartbeatDetector(manager);
        var emitter = new RetryableSseEmitter(60_000L, new MockHttpServletResponse());
        manager.register("k1", emitter);

        emitter.complete(); // already done

        // Should not throw
        detector.sendHeartbeats();
    }
}
