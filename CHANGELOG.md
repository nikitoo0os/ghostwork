# Changelog

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
