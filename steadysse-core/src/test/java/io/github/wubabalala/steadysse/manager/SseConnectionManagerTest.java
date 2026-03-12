package io.github.wubabalala.steadysse.manager;

import io.github.wubabalala.steadysse.config.SteadySseProperties;
import io.github.wubabalala.steadysse.emitter.RetryableSseEmitter;
import io.github.wubabalala.steadysse.exception.SseConnectionRejectedException;
import io.github.wubabalala.steadysse.lifecycle.SseLifecycleListener;
import io.github.wubabalala.steadysse.lifecycle.StreamEndStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SseConnectionManagerTest {

    private SseConnectionManager manager;

    @BeforeEach
    void setUp() {
        var props = new SteadySseProperties();
        props.setMaxConcurrent(2);
        manager = new SseConnectionManager(props);
    }

    @Test
    void registerAndRemove() {
        var emitter = createEmitter();
        manager.register("key1", emitter);
        assertThat(manager.getActiveCount()).isEqualTo(1);

        emitter.complete(); // triggers lifecycle → cleanup
        assertThat(manager.getActiveCount()).isEqualTo(0);
    }

    @Test
    void concurrentLimitRejectsExcess() {
        manager.register("k1", createEmitter());
        manager.register("k2", createEmitter());
        assertThrows(SseConnectionRejectedException.class, () ->
                manager.register("k3", createEmitter())
        );
    }

    @Test
    void semaphoreReleasedOnCompletion() {
        var e1 = createEmitter();
        manager.register("k1", e1);
        manager.register("k2", createEmitter());

        // At limit
        assertThrows(SseConnectionRejectedException.class, () ->
                manager.register("k3", createEmitter()));

        // Complete e1 → frees one permit
        e1.complete();

        // Should succeed now
        assertDoesNotThrow(() -> manager.register("k4", createEmitter()));
    }

    @Test
    void semaphoreReleasedOnTimeout() {
        var e1 = createEmitter();
        manager.register("k1", e1);
        manager.register("k2", createEmitter());

        e1.completeWithTimeout("test-timeout");

        // Permit freed
        assertDoesNotThrow(() -> manager.register("k3", createEmitter()));
    }

    @Test
    void semaphoreReleasedOnCancel() {
        var e1 = createEmitter();
        manager.register("k1", e1);
        manager.register("k2", createEmitter());

        e1.completeWithCancel();

        assertDoesNotThrow(() -> manager.register("k3", createEmitter()));
    }

    @Test
    void semaphoreReleasedOnError() {
        var e1 = createEmitter();
        manager.register("k1", e1);
        manager.register("k2", createEmitter());

        e1.completeWithError(new RuntimeException("fail"));

        assertDoesNotThrow(() -> manager.register("k3", createEmitter()));
    }

    @Test
    void cancelByPrefix() {
        var e1 = createEmitter();
        var e2 = createEmitter();
        var e3 = createEmitter();

        var props = new SteadySseProperties();
        props.setMaxConcurrent(10);
        var mgr = new SseConnectionManager(props);

        mgr.register("user:1:a", e1);
        mgr.register("user:1:b", e2);
        mgr.register("user:2:c", e3);

        mgr.cancelByPrefix("user:1:");

        assertThat(e1.isCancelled()).isTrue();
        assertThat(e2.isCancelled()).isTrue();
        assertThat(e3.isCancelled()).isFalse();
    }

    @Test
    void removeOnlyDoesNotCallComplete() {
        var emitter = createEmitter();
        manager.register("k1", emitter);
        manager.removeOnly("k1");
        assertThat(manager.getActiveCount()).isEqualTo(0);
        assertThat(emitter.isFinalCompleted()).isFalse();
    }

    @Test
    void markExecutionStartDoesNotThrow() {
        var emitter = createEmitter();
        manager.register("k1", emitter);
        assertDoesNotThrow(() -> manager.markExecutionStart("k1"));
    }

    @Test
    void markFirstChunkReceived() {
        var emitter = createEmitter();
        manager.register("k1", emitter);
        assertDoesNotThrow(() -> manager.markFirstChunkReceived("k1"));
    }

    @Test
    void updateActivity() {
        var emitter = createEmitter();
        manager.register("k1", emitter);
        assertDoesNotThrow(() -> manager.updateActivity("k1"));
    }

    @Test
    void cleanupIsExactlyOnce() {
        var emitter = createEmitter();
        var cleanupCount = new AtomicInteger(0);
        emitter.addLifecycleListener(new SseLifecycleListener() {
            @Override
            public void onComplete(StreamEndStatus status) {
                cleanupCount.incrementAndGet();
            }
        });

        manager.register("k1", emitter);

        // Complete twice — only first should trigger cleanup
        emitter.complete();
        emitter.complete();

        // Our listener + manager's listener = 1 call each (CAS prevents double)
        assertThat(cleanupCount.get()).isEqualTo(1);
        assertThat(manager.getActiveCount()).isEqualTo(0);
    }

    @Test
    void availablePermitsTracking() {
        assertThat(manager.getAvailablePermits()).isEqualTo(2);
        var e1 = createEmitter();
        manager.register("k1", e1);
        assertThat(manager.getAvailablePermits()).isEqualTo(1);
        e1.complete();
        assertThat(manager.getAvailablePermits()).isEqualTo(2);
    }

    private RetryableSseEmitter createEmitter() {
        return new RetryableSseEmitter(60_000L, new MockHttpServletResponse());
    }
}
