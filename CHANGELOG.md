# Changelog

## [0.1.0-SNAPSHOT] - Unreleased

### Added
- `FlushingSseEmitter` — three-layer flush for real-time SSE output
- `RetryableSseEmitter` — retry state machine with lifecycle management
- `SseLifecycleListener` — additive lifecycle callbacks with signal ordering contract
- `SseConnectionManager` — semaphore-based concurrency control with lifecycle binding
- `SseTimeoutDetector` — three-tier timeout detection (first-chunk, idle, hard)
- `SseHeartbeatDetector` — SSE comment frame heartbeats with disconnect detection
- `HttpCallCancellationManager` — lifecycle-linked upstream call auto-cancellation
- `SseEvents` / `SseEventPayload` — Jackson-based SSE protocol helpers
- `SseConnectionMetrics` — cumulative counters with Actuator endpoint
- `SseEmitterWrapper` — decorator base for send interception
- `SteadySseAutoConfiguration` — Spring Boot auto-configuration
- `TimeoutProvider` SPI — per-connection timeout overrides
- Sample Spring Boot application
