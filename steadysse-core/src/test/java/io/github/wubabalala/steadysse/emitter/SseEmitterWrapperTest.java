package io.github.wubabalala.steadysse.emitter;

import io.github.wubabalala.steadysse.lifecycle.SseLifecycleListener;
import io.github.wubabalala.steadysse.lifecycle.StreamEndStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterWrapperTest {

    @Test
    void delegatesSendToUnderlyingEmitter() throws IOException {
        var emitter = createEmitter();
        var wrapper = new SseEmitterWrapper(emitter);

        // Should not throw — MockHttpServletResponse silently accepts data
        wrapper.send("test data");
        wrapper.send("typed data", MediaType.TEXT_PLAIN);
        wrapper.send(SseEmitter.event().data("event data").build());
    }

    @Test
    void delegatesCompleteToUnderlyingEmitter() {
        var emitter = createEmitter();
        var status = new AtomicReference<StreamEndStatus>();
        emitter.addLifecycleListener(new SseLifecycleListener() {
            @Override
            public void onComplete(StreamEndStatus s) {
                status.set(s);
            }
        });

        var wrapper = new SseEmitterWrapper(emitter);
        wrapper.complete();

        assertThat(status.get()).isEqualTo(StreamEndStatus.SUCCESS);
    }

    @Test
    void delegatesCompleteWithErrorToUnderlyingEmitter() {
        var emitter = createEmitter();
        var captured = new AtomicReference<Throwable>();
        emitter.addLifecycleListener(new SseLifecycleListener() {
            @Override
            public void onError(Throwable e) {
                captured.set(e);
            }
        });

        var wrapper = new SseEmitterWrapper(emitter);
        var ex = new RuntimeException("test");
        wrapper.completeWithError(ex);

        assertThat(captured.get()).isSameAs(ex);
    }

    @Test
    void subclassCanInterceptSend() throws IOException {
        var emitter = createEmitter();
        var intercepted = new ArrayList<String>();

        var wrapper = new SseEmitterWrapper(emitter) {
            @Override
            public void send(Object object) throws IOException {
                intercepted.add(object.toString());
                super.send(object);
            }
        };

        wrapper.send("hello");

        assertThat(intercepted).containsExactly("hello");
    }

    @Test
    void exposesUnderlyingEmitter() {
        var emitter = createEmitter();
        var wrapper = new SseEmitterWrapper(emitter);
        assertThat(wrapper.getDelegate()).isSameAs(emitter);
    }

    private RetryableSseEmitter createEmitter() {
        return new RetryableSseEmitter(60_000L, new MockHttpServletResponse());
    }
}
