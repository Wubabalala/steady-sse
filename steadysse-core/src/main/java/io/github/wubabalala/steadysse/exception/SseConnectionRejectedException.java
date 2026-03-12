package io.github.wubabalala.steadysse.exception;

/**
 * Thrown when a new SSE connection is rejected due to concurrency limits.
 */
public class SseConnectionRejectedException extends RuntimeException {

    public SseConnectionRejectedException(String message) {
        super(message);
    }
}
