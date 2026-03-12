package io.github.wubabalala.steadysse.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class CompositeSseLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(CompositeSseLifecycleListener.class);
    private final List<SseLifecycleListener> listeners = new CopyOnWriteArrayList<>();

    public void add(SseLifecycleListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void fireComplete(StreamEndStatus status) {
        for (SseLifecycleListener l : listeners) {
            try {
                l.onComplete(status);
            } catch (Exception e) {
                log.warn("[SteadySSE] Lifecycle listener onComplete threw exception", e);
            }
        }
    }

    public void fireTimeout(String reason) {
        for (SseLifecycleListener l : listeners) {
            try {
                l.onTimeout(reason);
            } catch (Exception e) {
                log.warn("[SteadySSE] Lifecycle listener onTimeout threw exception", e);
            }
        }
    }

    public void fireError(Throwable error) {
        for (SseLifecycleListener l : listeners) {
            try {
                l.onError(error);
            } catch (Exception e) {
                log.warn("[SteadySSE] Lifecycle listener onError threw exception", e);
            }
        }
    }

    public void fireCancel() {
        for (SseLifecycleListener l : listeners) {
            try {
                l.onCancel();
            } catch (Exception e) {
                log.warn("[SteadySSE] Lifecycle listener onCancel threw exception", e);
            }
        }
    }
}
