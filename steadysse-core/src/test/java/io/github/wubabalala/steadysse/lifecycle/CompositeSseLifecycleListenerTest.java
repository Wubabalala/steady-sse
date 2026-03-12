package io.github.wubabalala.steadysse.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeSseLifecycleListenerTest {

    @Test
    void multipleListenersAllCalled() {
        var composite = new CompositeSseLifecycleListener();
        var called = new ArrayList<String>();

        composite.add(new SseLifecycleListener() {
            @Override public void onComplete(StreamEndStatus status) { called.add("A:" + status); }
        });
        composite.add(new SseLifecycleListener() {
            @Override public void onComplete(StreamEndStatus status) { called.add("B:" + status); }
        });

        composite.fireComplete(StreamEndStatus.SUCCESS);

        assertThat(called).containsExactly("A:SUCCESS", "B:SUCCESS");
    }

    @Test
    void listenerExceptionDoesNotBlockOthers() {
        var composite = new CompositeSseLifecycleListener();
        var called = new AtomicBoolean(false);

        composite.add(new SseLifecycleListener() {
            @Override public void onComplete(StreamEndStatus status) { throw new RuntimeException("boom"); }
        });
        composite.add(new SseLifecycleListener() {
            @Override public void onComplete(StreamEndStatus status) { called.set(true); }
        });

        composite.fireComplete(StreamEndStatus.SUCCESS);

        assertThat(called.get()).isTrue();
    }

    @Test
    void fireTimeout() {
        var composite = new CompositeSseLifecycleListener();
        var reason = new AtomicReference<String>();
        composite.add(new SseLifecycleListener() {
            @Override public void onTimeout(String r) { reason.set(r); }
        });

        composite.fireTimeout("idle timeout");

        assertThat(reason.get()).isEqualTo("idle timeout");
    }

    @Test
    void fireError() {
        var composite = new CompositeSseLifecycleListener();
        var captured = new AtomicReference<Throwable>();
        composite.add(new SseLifecycleListener() {
            @Override public void onError(Throwable e) { captured.set(e); }
        });

        var ex = new RuntimeException("test");
        composite.fireError(ex);

        assertThat(captured.get()).isSameAs(ex);
    }

    @Test
    void fireCancel() {
        var composite = new CompositeSseLifecycleListener();
        var called = new AtomicBoolean(false);
        composite.add(new SseLifecycleListener() {
            @Override public void onCancel() { called.set(true); }
        });

        composite.fireCancel();

        assertThat(called.get()).isTrue();
    }
}
