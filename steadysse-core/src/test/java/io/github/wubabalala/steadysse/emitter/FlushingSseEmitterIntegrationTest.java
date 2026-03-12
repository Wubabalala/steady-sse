package io.github.wubabalala.steadysse.emitter;

import io.github.wubabalala.steadysse.support.TestApplication;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class FlushingSseEmitterIntegrationTest {

    @LocalServerPort
    private int port;

    private OkHttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = new OkHttpClient.Builder()
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @Test
    void chunksArriveWithoutBuffering() throws Exception {
        var request = new Request.Builder()
                .url("http://localhost:" + port + "/test/flush-stream")
                .header("Accept", "text/event-stream")
                .build();

        var timestamps = new CopyOnWriteArrayList<Long>();
        var latch = new CountDownLatch(5);

        try (var response = httpClient.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
            var source = response.body().source();

            while (!source.exhausted()) {
                String line = source.readUtf8Line();
                if (line != null && line.startsWith("data:")) {
                    timestamps.add(System.currentTimeMillis());
                    latch.countDown();
                    if (latch.getCount() == 0) break;
                }
            }
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(timestamps).hasSizeGreaterThanOrEqualTo(5);

        // Verify flush penetration: gaps between consecutive chunks should reflect
        // the 200ms send interval (± tolerance). If buffering exists, multiple chunks
        // would arrive simultaneously (gap < 50ms).
        for (int i = 1; i < timestamps.size(); i++) {
            long gap = timestamps.get(i) - timestamps.get(i - 1);
            assertThat(gap)
                    .as("Gap between chunk %d and %d should reflect real-time flush, got %dms", i - 1, i, gap)
                    .isBetween(100L, 600L);
        }
    }

    @Test
    void clientDisconnectHandledGracefully() throws Exception {
        var request = new Request.Builder()
                .url("http://localhost:" + port + "/test/flush-stream")
                .header("Accept", "text/event-stream")
                .build();

        var call = httpClient.newCall(request);
        var response = call.execute();
        var source = response.body().source();

        // Read 2 chunks then disconnect
        int count = 0;
        while (!source.exhausted() && count < 2) {
            String line = source.readUtf8Line();
            if (line != null && line.startsWith("data:")) count++;
        }

        // Force disconnect
        call.cancel();
        response.close();

        // Give server time to detect the disconnect
        Thread.sleep(500);

        // If we get here without server-side uncaught exception, the test passes.
        // The server should handle IOException gracefully via FlushingSseEmitter's
        // trace-level logging (no error propagation).
    }
}
