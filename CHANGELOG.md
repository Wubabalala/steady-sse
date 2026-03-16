# Changelog

## [Unreleased]

### Fixed
- **Scheduling isolation** — replaced `@EnableScheduling` + `SchedulingConfigurer` with direct `TaskScheduler.scheduleAtFixedRate()` to avoid enabling global `@Scheduled` processing in host applications. Tasks are now properly cancelled on shutdown via `DisposableBean`.
- **Buffer-size fallback** — `FlushingSseEmitter.forceFlush()` now gracefully handles containers that reject `setBufferSize(0)`, disabling further attempts after the first failure instead of throwing on every flush.
- **Duplicate-key guard** — `SseConnectionManager.register()` rejects duplicate keys with a clear error message and uses `putIfAbsent` for race-condition safety. Null arguments are also rejected early.

## [0.1.0] - 2026-03-12

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
