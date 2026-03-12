package io.github.wubabalala.steadysse.lifecycle;

public interface SseLifecycleListener {
    default void onComplete(StreamEndStatus status) {}
    default void onTimeout(String reason) {}
    default void onError(Throwable error) {}
    default void onCancel() {}
}
