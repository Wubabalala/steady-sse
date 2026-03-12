package io.github.wubabalala.steadysse.integration;

import io.github.wubabalala.steadysse.cancel.HttpCallCancellationManager;
import io.github.wubabalala.steadysse.manager.SseConnectionManager;
import io.github.wubabalala.steadysse.manager.SseHeartbeatDetector;
import io.github.wubabalala.steadysse.manager.SseTimeoutDetector;
import io.github.wubabalala.steadysse.support.TestApplication;
import io.github.wubabalala.steadysse.support.TestSseController;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0 Acceptance tests using embedded Tomcat + OkHttp.
 * <p>
 * These tests verify real network behavior — flush penetration, heartbeat detection,
 * timeout cleanup, lifecycle auto-cancellation, and SSE protocol compliance.
 */
@SpringBootTest(
        classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "steady-sse.max-concurrent=20",
                "steady-sse.timeout.first-chunk=2s",
                "steady-sse.timeout.idle=3s",
                "steady-sse.timeout.hard=60s",
                "steady-sse.heartbeat-interval=60s",
                "steady-sse.cleanup-interval=60s"
        }
)
class SseAcceptanceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SseConnectionManager connectionManager;

    @Autowired
    private HttpCallCancellationManager cancellationManager;

    @Autowired
    private SseHeartbeatDetector heartbeatDetector;

    @Autowired
    private SseTimeoutDetector timeoutDetector;

    @Autowired
    private TestSseController testController;

    private OkHttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = new OkHttpClient.Builder()
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    // === Group 1: Flush + Lifecycle ===

    @Test
    void acceptance1_flushPenetration_chunksArriveWithoutDelay() throws Exception {
        var response = requestStream("/test/flush-stream");
        try {
            var reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
            var timestamps = new ArrayList<Long>();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    String data = line.substring(5);
                    // data format: "chunk-N:timestamp"
                    String[] parts = data.split(":");
                    if (parts.length >= 2) {
                        timestamps.add(Long.parseLong(parts[1]));
                    }
                }
            }

            assertThat(timestamps).hasSizeGreaterThanOrEqualTo(5);

            // Verify chunks arrive individually (not buffered) — gaps should be ~200ms, not 0
            for (int i = 1; i < timestamps.size(); i++) {
                long gap = timestamps.get(i) - timestamps.get(i - 1);
                assertThat(gap)
                        .as("Gap between chunk %d and %d should be ~200ms (flush working)", i - 1, i)
                        .isBetween(100L, 2000L);
            }
        } finally {
            response.close();
        }
    }

    @Test
    void acceptance22_managedStreamCompletesCleanly() throws Exception {
        int activeBefore = connectionManager.getActiveCount();

        var response = requestStream("/test/managed-stream");
        try {
            var reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
            var events = collectEvents(reader);

            // Should have message events (chunks) and a done event
            assertThat(events.stream().filter(e -> e.type.equals("message")).count())
                    .as("Should have at least 5 chunk events (type=message)")
                    .isGreaterThanOrEqualTo(5);
            assertThat(events.stream().anyMatch(e -> e.type.equals("done")))
                    .as("Should have a done event")
                    .isTrue();
        } finally {
            response.close();
        }

        // After stream completes, connection should be cleaned up
        Thread.sleep(500);
        assertThat(connectionManager.getActiveCount()).isEqualTo(activeBefore);
    }

    // === Group 2: Timeout ===

    @Test
    void acceptance4_firstChunkTimeoutDetected() throws Exception {
        int activeBefore = connectionManager.getActiveCount();

        // long-stream sends nothing — first-chunk timeout (2s) should fire
        // Use a short-timeout client to avoid OkHttp read timeout interfering
        var shortClient = new OkHttpClient.Builder()
                .readTimeout(8, TimeUnit.SECONDS)
                .build();
        var call = shortClient.newCall(sseRequest("/test/long-stream"));
        var response = call.execute();

        try {
            Thread.sleep(500); // let registration + executionStart happen

            // Wait for first-chunk timeout (2s) + margin
            Thread.sleep(2500);
            timeoutDetector.checkTimeouts();
            Thread.sleep(500);

            // Connection should be cleaned up by timeout detector
            assertThat(connectionManager.getActiveCount())
                    .as("Timed-out connection should be cleaned up")
                    .isEqualTo(activeBefore);
        } finally {
            call.cancel();
            response.close();
        }
    }

    // === Group 3: Heartbeat + Disconnect ===

    @Test
    void acceptance2_heartbeatKeepsConnectionAlive() throws Exception {
        var call = httpClient.newCall(sseRequest("/test/managed-stream"));
        var response = call.execute();

        try {
            Thread.sleep(300); // let registration happen

            int activeBefore = connectionManager.getActiveCount();
            assertThat(activeBefore).isGreaterThanOrEqualTo(1);

            // Send heartbeats — should not kill the connection
            heartbeatDetector.sendHeartbeats();
            Thread.sleep(200);

            assertThat(connectionManager.getActiveCount()).isGreaterThanOrEqualTo(1);
        } finally {
            // Wait for stream to complete naturally
            var reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
            while (reader.readLine() != null) { /* drain */ }
            response.close();
        }
    }

    @Test
    void acceptance3_heartbeatDetectsDisconnect() throws Exception {
        int activeBefore = connectionManager.getActiveCount();

        // Use long-stream which hangs after initial heartbeat
        var call = httpClient.newCall(sseRequest("/test/long-stream"));
        var response = call.execute();

        // Read initial comment to confirm connection is established
        var reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
        String firstLine = reader.readLine();
        assertThat(firstLine).isNotNull();

        assertThat(connectionManager.getActiveCount())
                .as("Connection should be registered")
                .isGreaterThan(activeBefore);

        // Client disconnects
        call.cancel();
        response.close();
        Thread.sleep(300);

        // Heartbeat should detect the dead connection and trigger cleanup
        heartbeatDetector.sendHeartbeats();
        Thread.sleep(500);

        // Retry heartbeat in case first one raced with TCP close
        heartbeatDetector.sendHeartbeats();
        Thread.sleep(500);

        assertThat(connectionManager.getActiveCount())
                .as("Disconnected connection must be cleaned up after heartbeat detection")
                .isEqualTo(activeBefore);
    }

    // === Group 5: Concurrency ===

    @Test
    void acceptance15_concurrencyLimitEnforced() throws Exception {
        // Fill all 20 permits by opening long-stream connections
        int maxConcurrent = connectionManager.getMaxConcurrent();
        var responses = new ArrayList<Response>();
        var calls = new ArrayList<okhttp3.Call>();

        try {
            for (int i = 0; i < maxConcurrent; i++) {
                var c = httpClient.newCall(sseRequest("/test/long-stream"));
                calls.add(c);
                responses.add(c.execute());
            }

            Thread.sleep(500); // let all registrations complete
            assertThat(connectionManager.getAvailablePermits())
                    .as("No permits should remain after filling all slots")
                    .isEqualTo(0);

            // Next connection must be rejected (HTTP 500 with SseConnectionRejectedException)
            var extraCall = httpClient.newCall(sseRequest("/test/long-stream"));
            var extraResponse = extraCall.execute();
            assertThat(extraResponse.code())
                    .as("Connection beyond limit must be rejected with server error")
                    .isEqualTo(500);
            extraResponse.close();
        } finally {
            for (var c : calls) c.cancel();
            for (var r : responses) r.close();
        }
    }

    // === Group 6: Call cancellation (lifecycle-linked) ===

    @Test
    void acceptance17_connectionCloseCancelsUpstreamCall() throws Exception {
        var call = httpClient.newCall(sseRequest("/test/cancel-stream"));
        var response = call.execute();
        Thread.sleep(500); // wait for server to register FakeCancellableCall

        var fakeCall = testController.getLastFakeCall();
        assertThat(fakeCall).isNotNull();
        assertThat(fakeCall.isCancelled()).isFalse();

        // Client disconnects
        call.cancel();
        response.close();
        Thread.sleep(200);

        // Trigger heartbeat to detect disconnect → lifecycle cleanup → auto-cancel
        heartbeatDetector.sendHeartbeats();
        Thread.sleep(500);

        assertThat(fakeCall.isCancelled())
                .as("Upstream CancellableCall.cancel() must be invoked on client disconnect")
                .isTrue();
        assertThat(cancellationManager.getActiveCallCount()).isEqualTo(0);
    }

    // === Group 7: Protocol ===

    @Test
    void acceptance19_sseProtocolCompliance() throws Exception {
        var response = requestStream("/test/managed-stream");
        try {
            // Verify SSE content type
            String contentType = response.header("Content-Type");
            assertThat(contentType).contains("text/event-stream");

            // Verify anti-buffering headers
            assertThat(response.header("X-Accel-Buffering")).isEqualTo("no");
            assertThat(response.header("Cache-Control")).contains("no-cache");

            var reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
            var events = collectEvents(reader);

            // Verify events have proper SSE format
            assertThat(events).isNotEmpty();
            // All events should have data
            for (var event : events) {
                assertThat(event.type).isNotBlank();
            }
            // Should have both message (chunk) and named (done) events
            assertThat(events.stream().anyMatch(e -> e.type.equals("message"))).isTrue();
            assertThat(events.stream().anyMatch(e -> e.type.equals("done"))).isTrue();
        } finally {
            response.close();
        }
    }

    // === Helpers ===

    private Response requestStream(String path) throws Exception {
        return httpClient.newCall(sseRequest(path)).execute();
    }

    private Request sseRequest(String path) {
        return new Request.Builder()
                .url("http://localhost:" + port + path)
                .header("Accept", "text/event-stream")
                .build();
    }

    private record SseEvent(String type, String data) {}

    /**
     * Parse SSE stream into events. Events without an explicit "event:" line
     * get type "message" (SSE default).
     */
    private List<SseEvent> collectEvents(BufferedReader reader) throws Exception {
        var events = new ArrayList<SseEvent>();
        String currentType = null;
        StringBuilder currentData = new StringBuilder();
        boolean hasData = false;

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("event:")) {
                currentType = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                if (hasData) currentData.append("\n");
                currentData.append(line.substring(5));
                hasData = true;
            } else if (line.isEmpty() && hasData) {
                events.add(new SseEvent(
                        currentType != null ? currentType : "message",
                        currentData.toString().trim()));
                currentType = null;
                currentData.setLength(0);
                hasData = false;
            }
        }
        if (hasData) {
            events.add(new SseEvent(
                    currentType != null ? currentType : "message",
                    currentData.toString().trim()));
        }
        return events;
    }
}
