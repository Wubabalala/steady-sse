package io.github.wubabalala.steadysse.emitter;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Delegating wrapper around {@link RetryableSseEmitter}.
 * <p>
 * Extend this class to intercept specific methods (e.g., content filtering on {@code send()}).
 * All methods delegate to the underlying emitter by default.
 * <p>
 * Example:
 * <pre>
 * public class FilteringSseEmitterWrapper extends SseEmitterWrapper {
 *     public FilteringSseEmitterWrapper(RetryableSseEmitter delegate) {
 *         super(delegate);
 *     }
 *
 *     &#64;Override
 *     public void send(Object object) throws IOException {
 *         // filter content here
 *         super.send(filtered);
 *     }
 * }
 * </pre>
 */
public class SseEmitterWrapper {

    private final RetryableSseEmitter delegate;

    public SseEmitterWrapper(RetryableSseEmitter delegate) {
        this.delegate = delegate;
    }

    public void send(Object object) throws IOException {
        delegate.send(object);
    }

    public void send(Object object, MediaType mediaType) throws IOException {
        delegate.send(object, mediaType);
    }

    public void send(SseEmitter.SseEventBuilder builder) throws IOException {
        delegate.send(builder);
    }

    public void sendHeartbeat() throws IOException {
        delegate.sendHeartbeat();
    }

    public void complete() {
        delegate.complete();
    }

    public void completeWithError(Throwable ex) {
        delegate.completeWithError(ex);
    }

    public void completeWithTimeout(String reason) {
        delegate.completeWithTimeout(reason);
    }

    public void completeWithCancel() {
        delegate.completeWithCancel();
    }

    public boolean isFinalCompleted() {
        return delegate.isFinalCompleted();
    }

    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    public RetryableSseEmitter getDelegate() {
        return delegate;
    }
}
