package io.github.wubabalala.steadysse.metrics;

import io.github.wubabalala.steadysse.lifecycle.StreamEndStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SseConnectionMetricsTest {

    @Test
    void initialCountersAreZero() {
        var metrics = new SseConnectionMetrics();
        var snapshot = metrics.snapshot(5, 10, 5);

        assertThat(snapshot.totalCompleted()).isEqualTo(0);
        assertThat(snapshot.totalErrors()).isEqualTo(0);
        assertThat(snapshot.totalTimeouts()).isEqualTo(0);
        assertThat(snapshot.totalCancelled()).isEqualTo(0);
        assertThat(snapshot.totalRejected()).isEqualTo(0);
    }

    @Test
    void recordCompletionIncrementsCorrectCounter() {
        var metrics = new SseConnectionMetrics();

        metrics.recordCompletion(StreamEndStatus.SUCCESS);
        metrics.recordCompletion(StreamEndStatus.SUCCESS);
        metrics.recordCompletion(StreamEndStatus.ERROR);
        metrics.recordCompletion(StreamEndStatus.TIMEOUT);
        metrics.recordCompletion(StreamEndStatus.CANCELLED);

        var snapshot = metrics.snapshot(0, 10, 10);
        assertThat(snapshot.totalCompleted()).isEqualTo(5); // all completions
        assertThat(snapshot.totalErrors()).isEqualTo(1);
        assertThat(snapshot.totalTimeouts()).isEqualTo(1);
        assertThat(snapshot.totalCancelled()).isEqualTo(1);
    }

    @Test
    void recordRejectionIncrementsRejectedCounter() {
        var metrics = new SseConnectionMetrics();

        metrics.recordRejection();
        metrics.recordRejection();

        var snapshot = metrics.snapshot(0, 10, 10);
        assertThat(snapshot.totalRejected()).isEqualTo(2);
    }

    @Test
    void snapshotIncludesLiveConnectionInfo() {
        var metrics = new SseConnectionMetrics();
        var snapshot = metrics.snapshot(3, 10, 7);

        assertThat(snapshot.active()).isEqualTo(3);
        assertThat(snapshot.maxConcurrent()).isEqualTo(10);
        assertThat(snapshot.availablePermits()).isEqualTo(7);
    }

    @Test
    void snapshotIsImmutable() {
        var metrics = new SseConnectionMetrics();
        var snap1 = metrics.snapshot(1, 10, 9);

        metrics.recordCompletion(StreamEndStatus.ERROR);
        var snap2 = metrics.snapshot(0, 10, 10);

        // snap1 should not reflect the later error
        assertThat(snap1.totalErrors()).isEqualTo(0);
        assertThat(snap2.totalErrors()).isEqualTo(1);
    }

    @Test
    void countersAreThreadSafe() throws InterruptedException {
        var metrics = new SseConnectionMetrics();
        int threads = 10;
        int perThread = 1000;

        var latch = new java.util.concurrent.CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    StreamEndStatus status = StreamEndStatus.values()[threadId % 4];
                    metrics.recordCompletion(status);
                }
                latch.countDown();
            }).start();
        }
        latch.await();

        var snapshot = metrics.snapshot(0, 10, 10);
        assertThat(snapshot.totalCompleted()).isEqualTo(threads * perThread);
    }
}
