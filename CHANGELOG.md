# Changelog

## Unreleased

- Added immutable task source metadata with the application call site and a
  bounded application-only call trace.
- Preserved executor and worker-thread metadata while exposing source class,
  method, file, and line diagnostics.

## 0.9.0

- Added validated `CorrelationId` values to operation and task views.
- Added immutable `GhostWorkContextSnapshot` capture/open semantics.
- Propagated operation, task, schedule, cancellation, and detached context
  without leaking pooled worker threads.
- Added immutable typed operation, task, and schedule lifecycle events.
- Preserved the legacy 0.8 event listener API as a compatibility bridge.
- Ensured tracked `CompletableFuture` results become observable only after the
  task and implicit operation reach their terminal states.

## 0.8.0

- Added full `ScheduledExecutorService` and `ScheduledFuture` decoration.
- Added separate schedule definitions and per-execution operations/root tasks.
- Added one-time, fixed-rate, and fixed-delay lifecycle tracking.
- Added late, overlap, long-running, cancellation, rejection, and retention
  diagnostics for scheduled work.
- Added schedule lifecycle events, shutdown diagnostics, conservative missed
  execution estimates, and operation retention leases.
- Added immutable paginated schedule and execution query APIs.
- Preserved the 0.7 operation, executor, cancellation, and diagnostic APIs.

## 0.7.0

- Added cooperative cancellation tokens, callbacks, causes, and diagnostics.
- Added explicit operation and task cancellation APIs.
- Added policy-driven owner and parent-child cancellation propagation.
- Added `OperationState.CANCELLED` without replacing timeout or abort states.
- Added detached tasks and explicit ghost/cancellation-resistant classification.
- Added cancellation lifecycle events and grace-period diagnostics.
- Preserved the 0.6 executor, operation, event, and diagnostic APIs.

## 0.6.0

- Added complete `ExecutorService` decoration and Spring MVC request ownership.
- Added executor/thread metadata, operation timelines, and queued diagnostics.
