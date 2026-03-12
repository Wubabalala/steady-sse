package io.github.wubabalala.steadysse.emitter;

import io.github.wubabalala.steadysse.lifecycle.SseLifecycleListener;
import io.github.wubabalala.steadysse.lifecycle.StreamEndStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryableSseEmitterTest {

    // === Retry State Machine ===

    @Test
    void completeInRetryModeIsIntercepted() {
        var emitter = createEmitter();
        emitter.setRetryCallback(error -> true);
        emitter.completeWithError(new RuntimeException("upstream fail"));

        assertThat(emitter.isFinalCompleted()).isFalse();
    }

    @Test
    void exitRetryModeAllowsSubsequentComplete() {
        var emitter = createEmitter();
        var status = new AtomicReference<StreamEndStatus>();
        emitter.addLifecycleListener(new SseLifecycleListener() {
            @Override
            public void onComplete(StreamEndStatus s) {
                status.set(s);
            }
        });

        emitter.setRetryCallback(error -> true);
        emitter.completeWithError(new RuntimeException("fail"));
        emitter.exitRetryMode();
        emitter.complete();

        assertThat(status.get()).isEqualTo(StreamEndStatus.SUCCESS);
    }

    @Test
    void maxRetriesPreventInfiniteLoop() {
        var emitter = createEmitter();
        emitter.setRetryCallback(error -> true); // always wants to retry

        // Exhaust retries (MAX_RETRIES = 5)
        for (int i = 0; i < 6; i++) {
            emitter.completeWithError(new RuntimeException("fail " + i));
            if (!emitter.isFinalCompleted()) {
                emitter.exitRetryMode();
            }
        }

        assertThat(emitter.isFinalCompleted()).isTrue();
    }

    @Test
    void cancelledEmitterDoesNotRetry() {
        var emitter = createEmitter();
        emitter.setRetryCallback(error -> true);
        emitter.markCancelled();
        emitter.completeWithError(new RuntimeException("fail"));

        assertThat(emitter.isFinalCompleted()).isTrue();
    }

    @Test
    void clientDisconnectDoesNotRetry() {
        var emitter = createEmitter();
        emitter.setRetryCallback(error -> true);
        emitter.completeWithError(new java.io.IOException("Broken pipe"));

        assertThat(emitter.isFinalCompleted()).isTrue();
    }

    @Test
    void noRetryCallbackMeansNoRetry() {
        var emitter = createEmitter();
        // No retry callback set
        emitter.completeWithError(new RuntimeException("fail"));

        assertThat(emitter.isFinalCompleted()).isTrue();
    }

    // === Lifecycle Listeners ===

    @Test
    void completeWithTimeoutFiresTimeoutListener() {
        var emitter = createEmitter();
        var reason = new AtomicReference<String>();
        emitter.addLifecycleListener(new SseLifecycleListener() {
            @Override
            public void onTimeout(String r) {
                reason.set(r);
            }
        });

        emitter.completeWithTimeout("idle timeout");

        assertThat(reason.get()).isEqualTo("idle timeout");
    }

    @Test
    void completeWithCancelFiresCancelListener() {
        var emitter = createEmitter();
        var called = new AtomicBoolean(false);
        emitter.addLifecycleListener(new SseLifecycleListener() {
            @Override
            public void onCancel() {
                called.set(true);
            }
        });

        emitter.completeWithCancel();

        assertThat(called.get()).isTrue();
    }

    @Test
    void completeWithErrorFiresErrorListener() {
        var emitter = createEmitter();
        var captured = new AtomicReference<Throwable>();
        emitter.addLifecycleListener(new SseLifecycleListener() {
            @Override
            public void onError(Throwable e) {
                captured.set(e);
            }
        });

        var ex = new RuntimeException("test error");
        emitter.completeWithError(ex);

        assertThat(captured.get()).isSameAs(ex);
    }

    // === Idempotency ===

    @Test
    void finalCompleteIsIdempotent() {
        var emitter = createEmitter();
        var count = new AtomicInteger(0);
        emitter.addLifecycleListener(new SseLifecycleListener() {
            @Override
            public void onComplete(StreamEndStatus status) {
                count.incrementAndGet();
            }
        });

        emitter.complete();
        emitter.complete(); // second call should be ignored

        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    void completeWithCancelIsIdempotent() {
        var emitter = createEmitter();
        var count = new AtomicInteger(0);
        emitter.addLifecycleListener(new SseLifecycleListener() {
            @Override
            public void onCancel() {
                count.incrementAndGet();
            }
        });

        emitter.completeWithCancel();
        emitter.completeWithCancel();

        assertThat(count.get()).isEqualTo(1);
    }

    // === Spring Callback Override Guards ===

    @Test
    void directOnCompletionThrows() {
        var emitter = createEmitter();
        assertThrows(UnsupportedOperationException.class,
                () -> emitter.onCompletion(() -> {}));
    }

    @Test
    void directOnTimeoutThrows() {
        var emitter = createEmitter();
        assertThrows(UnsupportedOperationException.class,
                () -> emitter.onTimeout(() -> {}));
    }

    @Test
    void directOnErrorThrows() {
        var emitter = createEmitter();
        assertThrows(UnsupportedOperationException.class,
                () -> emitter.onError(ex -> {}));
    }

    // === State Queries ===

    @Test
    void markCancelledIsIdempotent() {
        var emitter = createEmitter();
        emitter.markCancelled();
        emitter.markCancelled(); // should not throw
        assertThat(emitter.isCancelled()).isTrue();
    }

    @Test
    void retryCountTracking() {
        var emitter = createEmitter();
        emitter.setRetryCallback(error -> true);

        emitter.completeWithError(new RuntimeException("fail 1"));
        assertThat(emitter.getRetryCount()).isEqualTo(1);

        emitter.exitRetryMode();
        emitter.completeWithError(new RuntimeException("fail 2"));
        assertThat(emitter.getRetryCount()).isEqualTo(2);
    }

    @Test
    void getStateReturnsFormattedString() {
        var emitter = createEmitter();
        String state = emitter.getState();
        assertThat(state).contains("retryMode=false");
        assertThat(state).contains("finalCompleted=false");
        assertThat(state).contains("retryCount=0/5");
    }

    // ========== Helper ==========

    private RetryableSseEmitter createEmitter() {
        return new RetryableSseEmitter(60_000L, new MockHttpServletResponse());
    }
}
