package io.github.wubabalala.steadysse.support;

import io.github.wubabalala.steadysse.cancel.CancellableCall;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test helper implementing CancellableCall.
 * Tracks whether cancel() was actually invoked — used to verify
 * lifecycle-linked auto-cancellation behavior.
 */
public class FakeCancellableCall implements CancellableCall {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Override
    public void cancel() {
        cancelled.set(true);
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }
}
