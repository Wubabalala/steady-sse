package io.github.wubabalala.steadysse.cancel;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class CancellableCallTest {

    @Test
    void fakeCancellableCallTracksState() {
        var call = new FakeCancellableCall();
        assertThat(call.isCancelled()).isFalse();
        call.cancel();
        assertThat(call.isCancelled()).isTrue();
    }

    @Test
    void cancelIsIdempotent() {
        var call = new FakeCancellableCall();
        call.cancel();
        call.cancel(); // should not throw
        assertThat(call.isCancelled()).isTrue();
    }

    /**
     * Test helper implementing CancellableCall for unit testing.
     * This class is also useful as a reference for users implementing their own adapters.
     */
    static class FakeCancellableCall implements CancellableCall {
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
}
