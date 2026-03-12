# SteadySSE

A reliability layer for Spring MVC `SseEmitter` in production.

## The Problem

Spring's `SseEmitter` works fine in happy-path demos but breaks in production:

- **No flush control** — responses buffer in Tomcat/Nginx, causing multi-second delays
- **Callback overwrites** — `onCompletion()` uses setter semantics, so the last call wins
- **No retry support** — upstream failures kill the connection permanently
- **No timeout granularity** — only Spring's global timeout, no first-chunk or idle tracking
- **No concurrency limits** — unbounded connections can exhaust server resources
- **No disconnect detection** — dead connections linger until Spring's timeout fires

SteadySSE solves these with a drop-in library extracted from a production system handling thousands of concurrent SSE streams.

## What It Does

- **Three-layer flush** — `setBufferSize(0)` + `flushBuffer()` + `outputStream.flush()` for real-time output
- **Lifecycle listeners** — additive (not setter) callbacks with guaranteed signal ordering
- **Retry state machine** — intercept errors, retry upstream, keep the SSE connection alive
- **Three-tier timeout** — first-chunk, idle, and hard timeout detection
- **Heartbeat** — SSE comment frames detect dead connections without resetting idle timers
- **Concurrency control** — semaphore-based connection limiting with rejection handling
- **Upstream call cancellation** — auto-cancel HTTP calls when SSE connections end
- **Metrics** — cumulative counters + Actuator endpoint at `/actuator/steadysse`

## What It Does NOT Do

- Business logic (auth, billing, moderation)
- WebSocket or gRPC support
- Client-side reconnection (that's the browser's job via `EventSource`)
- Message persistence or replay

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>io.github.wubabalala</groupId>
    <artifactId>steadysse-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Write a Controller

```java
@RestController
public class StreamController {

    private final SseConnectionManager connectionManager;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse response) {
        var emitter = new RetryableSseEmitter(60_000L, response);
        String key = "stream:" + UUID.randomUUID();
        connectionManager.register(key, emitter);
        connectionManager.markExecutionStart(key);

        executor.submit(() -> {
            try {
                connectionManager.markFirstChunkReceived(key);
                for (String chunk : generateData()) {
                    emitter.send(SseEvents.chunk(chunk));
                }
                emitter.send(SseEvents.done());
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
```

### 3. Configure (Optional)

```yaml
steady-sse:
  max-concurrent: 100
  heartbeat-interval: 10s
  timeout:
    first-chunk: 30s
    idle: 60s
    hard: 300s
```

All beans are auto-configured. Override any by defining your own `@Bean`.

## Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `steady-sse.max-concurrent` | 100 | Maximum concurrent SSE connections |
| `steady-sse.heartbeat-interval` | 10s | Interval between heartbeat comment frames |
| `steady-sse.cleanup-interval` | 30s | Interval between timeout checks |
| `steady-sse.timeout.first-chunk` | 30s | Max wait for first data after execution starts |
| `steady-sse.timeout.idle` | 60s | Max silence between chunks |
| `steady-sse.timeout.hard` | 300s | Absolute connection lifetime |

## Architecture

See [docs/architecture.md](docs/architecture.md) for component design and signal flow.

## Requirements

- Java 17+
- Spring Boot 3.2+
- Servlet container (Tomcat, Jetty, Undertow)

## License

Apache License 2.0
