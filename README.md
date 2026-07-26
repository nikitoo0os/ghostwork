# GhostWork

[![Build](https://github.com/nikitoo0os/ghostwork/actions/workflows/ci.yml/badge.svg)](https://github.com/nikitoo0os/ghostwork/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.nikitoo0os/ghostwork)](https://central.sonatype.com/artifact/io.github.nikitoo0os/ghostwork)
[![Latest release](https://img.shields.io/github/v/tag/nikitoo0os/ghostwork)](https://github.com/nikitoo0os/ghostwork/tags)
[![Java 21](https://img.shields.io/badge/Java-21%2B-007396)](https://adoptium.net/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

GhostWork is a lightweight Java library for tracking asynchronous work submitted to executors.

It groups tasks under logical operations, records task lifecycle transitions, detects tasks that keep running after their parent operation has finished, and exposes diagnostics through a small public API.

## Installation

GhostWork is available from Maven Central:

```xml
<dependency>
    <groupId>io.github.nikitoo0os</groupId>
    <artifactId>ghostwork</artifactId>
    <version>0.9.0</version>
</dependency>
```

Requirements:

* Java 21 or later
* Maven 3.8 or later

## Why GhostWork

Asynchronous Java code often submits work to an `ExecutorService` and then loses visibility into what happened to that work.

GhostWork helps answer questions such as:

* Which tasks were started by this operation?
* Did a task complete, fail, get rejected, or get cancelled?
* Did an operation finish while one of its tasks was still running?
* Which tasks have been running longer than expected?
* What lifecycle events happened during execution?

## Features

* Operation lifecycle tracking
* Task lifecycle tracking
* Thread-safe lifecycle transitions
* Context propagation across executor threads
* `Runnable` and `Callable<T>` tracking
* Complete standard `ExecutorService` decoration
* Complete standard `ScheduledExecutorService` decoration
* One-time, fixed-rate, and fixed-delay schedule tracking
* Separate schedule definitions and per-run operations
* Late, overlapping, and long-running execution diagnostics
* Conservative missed fixed-rate execution estimates
* Schedule lifecycle events and shutdown diagnostics
* Paginated schedule and execution query API
* `Future.cancel(...)` tracking
* Cooperative cancellation tokens and callbacks
* Policy-driven operation and parent-task cancellation
* Explicit detached tasks
* Cancellation grace-period diagnostics
* Executor rejection tracking
* Implicit operation creation when no operation is active
* Ghost task detection
* Stuck task detection
* Separate queued-stuck and running-stuck diagnostics
* Executor and worker-thread metadata
* Event listener API
* Immutable typed lifecycle event API
* Validated correlation IDs and context snapshots
* Periodic monitoring
* Configurable in-memory retention
* Read-only public views

Observability backends remain optional and live in the
[`ghostwork-observability`](https://github.com/nikitoo0os/ghostwork-observability)
repository. Core has no Spring, Micrometer, OpenTelemetry, SLF4J, Jackson, or
Prometheus dependency.

## Quick Start

```java
import io.nikitoo0os.GhostWork;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Example {

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        GhostWork ghostWork = GhostWork.create(executor);

        ghostWork.call(
                "CustomerImport",
                () -> {
                    ghostWork.executor()
                            .submit(
                                    "LoadCustomers",
                                    () -> {
                                        // async work
                                    }
                            )
                            .get(1, TimeUnit.SECONDS);

                    return null;
                }
        );

        var operation = ghostWork.operations().getFirst();
        var tasks = ghostWork.tasks(operation.id());

        System.out.println(operation.state());
        System.out.println(tasks.getFirst().state());

        ghostWork.executor().shutdown();
    }
}
```

## Implicit Operations

If a task is submitted without an active operation, GhostWork creates an implicit operation automatically:

```java
ghostWork.executor()
        .submit(
                "StandaloneTask",
                () -> {
                    // work
                }
        );
```

The task is registered under an operation named:

```text
Implicit:StandaloneTask
```

This is useful for applications that want task tracking without manually wrapping every call in `ghostWork.run(...)` or `ghostWork.call(...)`.

## Scheduled Work

Decorate an existing scheduler without changing its ownership or execution
policies:

```java
ScheduledExecutorService delegate =
        Executors.newScheduledThreadPool(2);
var scheduler = ghostWork.decorateScheduler(delegate);

ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
        ScheduleOptions.manual("RefreshCatalog"),
        catalogService::refresh,
        0,
        30,
        TimeUnit.SECONDS
);
```

A recurring schedule is stored once. Every invocation creates a distinct
operation and root task:

```text
RefreshCatalog
|- execution #1 -> Operation -> scheduled invocation task
|- execution #2 -> Operation -> scheduled invocation task
`- execution #3 -> Operation -> scheduled invocation task
```

Read-only diagnostics are available without exposing mutable registry objects:

```java
var schedules = ghostWork.schedules(ScheduleQuery.firstPage());
var executions = ghostWork.scheduleExecutions(
        schedules.getFirst().id(),
        ScheduleExecutionQuery.recent()
);
```

`TrackedScheduledFuture` preserves the delegate `ScheduledFuture` contract.
Cancellation stops future invocations and `cancel(true)` also propagates the
request to an active execution operation.

`TrackingScheduledExecutorService.shutdownDiagnostics()` reports the shutdown
method, active and expected executions at the boundary, tasks returned by
`shutdownNow()`, and observed termination. Missed fixed-rate counts are
conservative estimates and expose `missedEstimateExact=false`; scheduler
coalescing policies can prevent exact reconstruction.

## Tasks In The Current Thread

Database transactions, request state, and security context are commonly bound to
the calling thread. Use `runTask(...)` or `callTask(...)` to measure a named step
without moving it to the delegate executor:

```java
ghostWork.call("LoadProductPage", () -> {
    var products = ghostWork.executor().callTask(
            "Query products",
            productRepository::findAll
    );

    ghostWork.executor().runTask(
            "Map response",
            () -> mapProducts(products)
    );

    return products;
});
```

These methods require an active operation and create normal tracked tasks with
the same lifecycle, timing, events, and diagnostics as submitted tasks.

## Integrations

The `ghostwork` artifact is framework-independent and does not depend on Spring.

Spring AOP support lives in a separate artifact:

```xml
<dependency>
    <groupId>io.github.nikitoo0os</groupId>
    <artifactId>ghostwork-spring</artifactId>
    <version>0.9.0</version>
</dependency>
```

The optional dashboard lives in:

```xml
<dependency>
    <groupId>io.github.nikitoo0os</groupId>
    <artifactId>ghostwork-dashboard-spring</artifactId>
    <version>0.9.0</version>
</dependency>
```

A runnable compatibility example using core, Spring scheduling, Spring MVC,
and the dashboard together is available in
[`examples/spring-boot-scheduled-consumer`](examples/spring-boot-scheduled-consumer).
Its integration test starts a real Spring Boot server and verifies the complete
schedule definition -> execution -> operation -> root task chain.

## Diagnostics

GhostWork exposes read-only views for operations and tasks:

```java
var operations = ghostWork.operations();
var tasks = ghostWork.tasks(operationId);
```

Detect ghost tasks:

```java
var ghostTasks = ghostWork.ghostTasks(operationId);
```

A ghost task is a task that is still running after its parent operation has already finished.

Detect stuck tasks:

```java
var stuckTasks = ghostWork.stuckTasks(
        operationId,
        Duration.ofSeconds(30)
);
```

A stuck task is a running task whose execution duration is greater than the provided threshold.

Queued and running delays can also be inspected separately:

```java
var queued = ghostWork.stuckQueuedTasks(
        operationId,
        Duration.ofSeconds(10)
);
var running = ghostWork.stuckRunningTasks(
        operationId,
        Duration.ofSeconds(30)
);
var allTaskDiagnostics = ghostWork.taskDiagnostics(operationId);
```

A queued-stuck task is in `SUBMITTED` state and has waited longer than the
threshold without starting. A running-stuck task is in `RUNNING` state and has
executed longer than the threshold. The existing `stuckTasks(...)` method keeps
its `0.4.x` meaning and reports running tasks only.

`TaskDiagnostics` exposes `submittedAt`, `startedAt`, queue duration, execution
duration, executor bean/class metadata, submission source, and worker-thread
metadata. Durations are snapshots observed when the diagnostic query runs.

Create a report:

```java
var report = ghostWork.report(Duration.ofSeconds(30));
```

The report contains:

* operations
* tasks
* ghost tasks
* stuck tasks

## Events

GhostWork can publish lifecycle events:

```java
ghostWork.addEventListener(event -> {
    System.out.println(event.type());
    System.out.println(event.operation());
    System.out.println(event.task());
});
```

Supported event types:

* `OPERATION_COMPLETED`
* `OPERATION_FAILED`
* `OPERATION_TIMED_OUT`
* `OPERATION_ABORTED`
* `OPERATION_CANCELLED`
* `OPERATION_CANCELLATION_REQUESTED`
* `TASK_CANCELLATION_REQUESTED`
* `TASK_CANCELLATION_ACCEPTED`
* `TASK_INTERRUPT_REQUESTED`
* `TASK_CANCELLATION_OBSERVED`
* `TASK_CANCELLATION_COMPLETED`
* `TASK_CANCELLATION_GRACE_PERIOD_EXCEEDED`
* `CANCELLATION_CALLBACK_FAILED`
* `TASK_SUBMITTED`
* `TASK_STARTED`
* `TASK_COMPLETED`
* `TASK_FAILED`
* `TASK_REJECTED`
* `TASK_CANCELLED`

## Task States

Tasks can move through the following states:

```text
CREATED
SUBMITTED
RUNNING
COMPLETED
FAILED
REJECTED
CANCELLED
```

Final task states are:

* `COMPLETED`
* `FAILED`
* `REJECTED`
* `CANCELLED`

## Operation States

Operations can move through the following states:

```text
RUNNING
COMPLETED
FAILED
TIMED_OUT
ABORTED
CANCELLED
```

Final operation states are:

* `COMPLETED`
* `FAILED`
* `TIMED_OUT`
* `ABORTED`
* `CANCELLED`

`ABORTED` is used by externally owned operations, such as Spring MVC requests
whose client disconnected. `CANCELLED` is reserved for explicit cancellation;
timeouts and client aborts retain their original operation state.

## Cancellation Model

Cancellation is a separate lifecycle dimension. A running task can have
`state=RUNNING` and `cancellation.status=PENDING` at the same time. A request,
an accepted `Future.cancel(...)`, an interrupt attempt, cooperative observation,
and terminal cancellation are recorded independently.

Cancel an operation or an individual task:

```java
CancellationResult operationResult =
        ghostWork.cancelOperation(operationId);

CancellationResult taskResult =
        ghostWork.cancelTask(taskId);
```

`CancellationResult` reports whether the target was found, whether the first
request won, the operation state before explicit cancellation, targeted queued
and running tasks, `Future.cancel(...)` attempts and acceptances, and tasks that
remain active.

### Cooperative Tasks

Code running in a tracked task can read its immutable token:

```java
CancellationToken token =
        GhostWorkContext.currentCancellationToken();

while (hasMoreData()) {
    token.throwIfCancellationRequested();
    processNextBatch();
}
```

Outside a tracked task, `currentCancellationToken()` returns a non-cancellable
token. Throwing the standard `CancellationException` after a tracked request
finishes the task as `CANCELLED`; an unrelated exception remains `FAILED`.

Register resource-specific cleanup without asking GhostWork to guess how a
resource should be stopped:

```java
CancellationToken token =
        GhostWorkContext.currentCancellationToken();
ExternalCall call = client.newCall(request);

try (var registration = token.onCancellation(call::cancel)) {
    return call.execute();
}
```

Callbacks run at most once, may be removed by closing their registration, and
are isolated from one another. A callback registered after cancellation runs
immediately.

### Detached Tasks

Legitimate background work can explicitly outlive its operation:

```java
ghostWork.executor().submit(
        TaskOptions.detached("WriteAuditLog"),
        auditService::write
);
```

Detached tasks remain visible and can still be cancelled explicitly with
`cancelTask(...)`, but owner and parent cancellation do not propagate to them
and they are excluded from default ghost-task queries.

### Interrupt And Grace Semantics

`Future.cancel(true)` and thread interruption are cooperative mechanisms. They
do not guarantee that arbitrary Java code, blocking I/O, or third-party
libraries stop producing side effects. If user code catches
`InterruptedException`, clears the flag, and returns normally, the task is
`COMPLETED`; diagnostics still show that interruption was requested, but cannot
claim it was observed.

Use `refreshCancellationDiagnostics(gracePeriod)` in plain Java to classify
tasks that remain active after a cancellation request. Spring integration runs
this check automatically from the configured grace period. Such a task keeps
its real task state and receives
`classification=CANCELLATION_IGNORED`.

Cancellation diagnostics are immutable and queryable without exposing the
registry:

```java
ghostWork.taskCancellation(taskId);
ghostWork.cancellationPendingTasks();
ghostWork.cancellationIgnoredTasks();
ghostWork.cancelledTasks();
```

## Migration From 0.8

Version 0.9 is additive. Existing operation, task, cancellation, executor,
schedule, legacy event, and diagnostic APIs remain available. Public views now
also expose validated correlation IDs. New typed lifecycle listeners are added
through `addLifecycleEventListener(...)`.

## Retention

GhostWork keeps diagnostics in memory. Configure how completed operations are
removed by age and count:

```java
var retention = new RetentionPolicy(
        10_000,
        Duration.ofHours(24),
        Duration.ofMinutes(5)
);

GhostWork ghostWork = GhostWork.create(executor, retention);
ghostWork.startRetentionCleanup(scheduledExecutorService);
```

`cleanup()` can also be called manually. Operations with active tasks are never
removed.

## Monitoring

GhostWork can run periodic diagnostics with a scheduler:

```java
var monitor = ghostWork.monitor(scheduledExecutorService);

monitor.start(
        Duration.ofSeconds(30),
        Duration.ofSeconds(10),
        report -> {
            System.out.println(report);
        }
);
```

## Building From Source

```bash
git clone https://github.com/nikitoo0os/ghostwork.git
cd ghostwork
mvn clean verify
```

The built jar is created at:

```text
target/ghostwork-0.9.0.jar
```

## Current Scope

GhostWork is an in-memory tracking library.

It does not currently provide:

* persistent storage
* distributed task tracking
* automatic threshold-crossing events (queue/running diagnostics are available
  through the query API)

Metrics, OpenTelemetry, MDC, and Actuator adapters are optional artifacts from
`ghostwork-observability`; they are intentionally not core dependencies.

Spring MVC request ownership is available separately:

```xml
<dependency>
    <groupId>io.github.nikitoo0os</groupId>
    <artifactId>ghostwork-spring-webmvc</artifactId>
    <version>0.9.0</version>
</dependency>
```

`TrackingExecutorService` implements the complete standard `ExecutorService`
contract and delegates executor ownership and shutdown semantics to the supplied
executor.

## Roadmap

GhostWork is actively evolving. Planned areas include:

* richer diagnostic DTOs for ghost and stuck tasks
* metrics and observability integrations
* metrics and tracing adapters for schedule diagnostics
* additional production examples for cancellation and retention policies

## License

GhostWork is licensed under the Apache License, Version 2.0.
