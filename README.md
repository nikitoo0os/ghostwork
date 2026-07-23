# GhostWork

GhostWork is a lightweight Java library for tracking asynchronous work submitted to executors.

It groups tasks under logical operations, records task lifecycle transitions, detects tasks that keep running after their parent operation has finished, and exposes diagnostics through a small public API.

## Installation

GhostWork is available from Maven Central:

```xml
<dependency>
    <groupId>io.github.nikitoo0os</groupId>
    <artifactId>ghostwork</artifactId>
    <version>0.1.0</version>
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
* `Future.cancel(...)` tracking
* Executor rejection tracking
* Implicit operation creation when no operation is active
* Ghost task detection
* Stuck task detection
* Event listener API
* Periodic monitoring
* Read-only public views

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

## Integrations

The `ghostwork` artifact is framework-independent and does not depend on Spring.

Spring AOP support lives in a separate artifact:

```xml
<dependency>
    <groupId>io.github.nikitoo0os</groupId>
    <artifactId>ghostwork-spring</artifactId>
    <version>0.2.0</version>
</dependency>
```

The optional dashboard lives in:

```xml
<dependency>
    <groupId>io.github.nikitoo0os</groupId>
    <artifactId>ghostwork-dashboard-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

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
* `TASK_STARTED`
* `TASK_COMPLETED`
* `TASK_FAILED`
* `TASK_REJECTED`
* `TASK_CANCELLED`

## Task States

Tasks can move through the following states:

```text
CREATED
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
```

Final operation states are:

* `COMPLETED`
* `FAILED`
* `TIMED_OUT`

## Cancellation

GhostWork tracks cancellation through returned futures:

```java
var future = ghostWork.executor()
        .submit(
                "ImportTask",
                () -> {
                    // work
                }
        );

future.cancel(false);
```

If cancellation succeeds, the task transitions to `CANCELLED` and a `TASK_CANCELLED` event is published.

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
target/ghostwork-0.2.0-SNAPSHOT.jar
```

## Current Scope

GhostWork is an in-memory tracking library.

It does not currently provide:

* persistent storage
* distributed task tracking
* metrics export
* OpenTelemetry integration
* a complete drop-in replacement for every `ExecutorService` method

`TrackingExecutorService` should be treated as a tracked submission facade around an existing executor.

## Roadmap

GhostWork is actively evolving. Planned areas include:

* richer diagnostic DTOs for ghost and stuck tasks
* retention policies for completed operations and tasks
* metrics and observability integrations
* broader executor decoration coverage
* production examples for Spring applications

## License

GhostWork is licensed under the Apache License, Version 2.0.
