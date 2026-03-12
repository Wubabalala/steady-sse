package io.github.wubabalala.steadysse.cancel;

/**
 * Abstraction over an upstream HTTP call that can be cancelled.
 * Decouples SteadySSE from specific HTTP clients (OkHttp, HttpClient, etc.).
 */
public interface CancellableCall {
    void cancel();
    boolean isCancelled();
}
