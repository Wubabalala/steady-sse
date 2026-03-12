# SteadySSE Architecture

## Component Overview

```
┌─────────────────────────────────────────────────────┐
│                  Your Controller                     │
│  creates RetryableSseEmitter, registers with manager │
└──────────┬──────────────────────────────┬───────────┘
           │                              │
           ▼                              ▼
┌─────────────────────┐     ┌──────────────────────────┐
│ SseConnectionManager │     │ HttpCallCancellationMgr  │
│  - semaphore control │     │  - upstream call tracking │
│  - lifecycle binding │     │  - lifecycle-linked cancel│
│  - activity tracking │     └──────────────────────────┘
└──────────┬──────────┘
           │ manages
           ▼
┌─────────────────────────────────────────┐
│         RetryableSseEmitter             │
│  ┌─────────────────────────────┐        │
│  │    FlushingSseEmitter       │        │
│  │  - three-layer flush        │        │
│  │  - anti-buffering headers   │        │
│  └─────────────────────────────┘        │
│  - retry state machine                  │
│  - lifecycle listener (additive)        │
│  - override guards (onCompletion, etc.) │
│  - CAS-based idempotent completion      │
└──────────┬──────────────────────────────┘
           │ detected by
           ▼
┌──────────────────────┐  ┌─────────────────────┐
│ SseTimeoutDetector   │  │ SseHeartbeatDetector │
│  - first-chunk       │  │  - SSE comment frames│
│  - idle              │  │  - disconnect detect │
│  - hard              │  │  - no activity reset │
└──────────────────────┘  └─────────────────────┘
```

## Signal Ordering Contract

All exit paths funnel through `RetryableSseEmitter.doFinalComplete()`:

1. **Detail signal first** (provides context):
   - `onTimeout(reason)` for timeout exits
   - `onCancel()` for cancellation exits
   - `onError(throwable)` for error exits
   - (no detail signal for success)

2. **Terminal signal last** (always fires exactly once):
   - `onComplete(StreamEndStatus)` — do cleanup here

This ordering is enforced by CAS (`finalCompleted.compareAndSet(false, true)`).

## Spring Callback Trap

Spring's `SseEmitter.onCompletion()` uses **setter semantics** — each call replaces the previous callback. SteadySSE solves this:

1. `RetryableSseEmitter` constructor is the **sole entry point** for `super.onCompletion/onTimeout/onError`
2. Override methods throw `UnsupportedOperationException` to prevent accidental overwrite
3. All external code must use `addLifecycleListener()` which is additive

## Retry State Machine

```
                   ┌──────────────────────┐
                   │     NORMAL           │
                   └──────────┬───────────┘
                              │ completeWithError()
                              ▼
                   ┌──────────────────────┐
                   │  Check eligibility:  │
                   │  1. max retries?     │──→ FINALIZED
                   │  2. client disconnect│──→ FINALIZED
                   │  3. cancelled?       │──→ FINALIZED
                   │  4. callback says no?│──→ FINALIZED
                   └──────────┬───────────┘
                              │ callback says yes
                              ▼
                   ┌──────────────────────┐
                   │    RETRY MODE        │
                   │  - complete() suppressed
                   │  - connection stays alive
                   └──────────┬───────────┘
                              │ exitRetryMode()
                              ▼
                   ┌──────────────────────┐
                   │     NORMAL           │
                   │  (ready for new data)│
                   └──────────────────────┘
```

## Three-Tier Timeout

| Tier | Measures | Starts From | Ends When |
|------|----------|-------------|-----------|
| First-chunk | Provider responsiveness | `markExecutionStart()` | `markFirstChunkReceived()` |
| Idle | Data flow health | Last `send()` | Next `send()` |
| Hard | Absolute lifetime | Registration | Connection end |

Key design: `executionStart` is separate from registration to exclude queue wait time.

## Heartbeat Design

Heartbeats send SSE comment frames (`: heartbeat\n\n`) which:
- Keep proxies/load balancers from closing idle connections
- Detect dead connections (send failure = client gone)
- Do **NOT** update activity time (so idle timeout still works for stuck connections)
