package io.github.wubabalala.steadysse.cancel;

import io.github.wubabalala.steadysse.lifecycle.StreamEndStatus;
import io.github.wubabalala.steadysse.support.FakeCancellableCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class HttpCallCancellationManagerTest {

    private HttpCallCancellationManager manager;

    @BeforeEach
    void setUp() {
        manager = new HttpCallCancellationManager();
    }

    // === Basic operations ===

    @Test
    void registerAndCancelByKey() {
        var call = new FakeCancellableCall();
        manager.registerCall("key1", call);
        manager.cancelByKey("key1", "test");
        assertThat(call.isCancelled()).isTrue();
    }

    @Test
    void cancelUnknownKeyDoesNotThrow() {
        assertDoesNotThrow(() -> manager.cancelByKey("unknown", "test"));
    }

    @Test
    void cancelRemovesEntry() {
        var call = new FakeCancellableCall();
        manager.registerCall("key1", call);
        manager.cancelByKey("key1", "test");
        assertThat(manager.getActiveCallCount()).isEqualTo(0);
    }

    @Test
    void cancelAllCancelsEverything() {
        var call1 = new FakeCancellableCall();
        var call2 = new FakeCancellableCall();
        manager.registerCall("k1", call1);
        manager.registerCall("k2", call2);
        manager.cancelAll("shutdown");
        assertThat(call1.isCancelled()).isTrue();
        assertThat(call2.isCancelled()).isTrue();
        assertThat(manager.getActiveCallCount()).isEqualTo(0);
    }

    @Test
    void removeByKeyDoesNotCancel() {
        var call = new FakeCancellableCall();
        manager.registerCall("key1", call);
        manager.removeByKey("key1");
        assertThat(call.isCancelled()).isFalse();
        assertThat(manager.getActiveCallCount()).isEqualTo(0);
    }

    @Test
    void shutdownCallsCancelAll() {
        var call = new FakeCancellableCall();
        manager.registerCall("key1", call);
        manager.shutdown(); // @PreDestroy no-arg method
        assertThat(call.isCancelled()).isTrue();
        assertThat(manager.getActiveCallCount()).isEqualTo(0);
    }

    // === Lifecycle-linked auto-cancellation ===

    @Test
    void lifecycleAutoCancel_onComplete() {
        var call = new FakeCancellableCall();
        manager.registerCall("conn:1", call);

        var listener = manager.createLifecycleListenerFor("conn:1");
        listener.onComplete(StreamEndStatus.SUCCESS);

        assertThat(call.isCancelled()).isTrue();
        assertThat(manager.getActiveCallCount()).isEqualTo(0);
    }

    @Test
    void lifecycleAutoCancel_onTimeout() {
        var call = new FakeCancellableCall();
        manager.registerCall("conn:2", call);

        var listener = manager.createLifecycleListenerFor("conn:2");
        listener.onTimeout("idle timeout");

        assertThat(call.isCancelled()).isTrue();
    }

    @Test
    void lifecycleAutoCancel_onError() {
        var call = new FakeCancellableCall();
        manager.registerCall("conn:3", call);

        var listener = manager.createLifecycleListenerFor("conn:3");
        listener.onError(new RuntimeException("upstream failure"));

        assertThat(call.isCancelled()).isTrue();
    }

    @Test
    void lifecycleAutoCancel_onCancel() {
        var call = new FakeCancellableCall();
        manager.registerCall("conn:4", call);

        var listener = manager.createLifecycleListenerFor("conn:4");
        listener.onCancel();

        assertThat(call.isCancelled()).isTrue();
    }

    @Test
    void noCallRegistered_lifecycleDoesNotThrow() {
        var listener = manager.createLifecycleListenerFor("nonexistent");
        assertDoesNotThrow(() -> listener.onComplete(StreamEndStatus.SUCCESS));
        assertDoesNotThrow(() -> listener.onTimeout("test"));
        assertDoesNotThrow(() -> listener.onError(new RuntimeException("test")));
        assertDoesNotThrow(() -> listener.onCancel());
    }
}
