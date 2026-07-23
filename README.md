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
* Optional Spring AOP integration

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

## Spring Usage

GhostWork includes an optional Spring AOP adapter.

For Spring Boot applications, add AOP support:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

Register GhostWork beans:

```java
import io.nikitoo0os.GhostWork;
import io.nikitoo0os.annotation.TrackedOperationInvoker;
import io.nikitoo0os.annotation.TrackedOperationResolver;
import io.nikitoo0os.spring.TrackedOperationAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableAspectJAutoProxy
public class GhostWorkConfig {

    @Bean
    ExecutorService applicationExecutor() {
        return Executors.newFixedThreadPool(8);
    }

    @Bean
    GhostWork ghostWork(ExecutorService applicationExecutor) {
        return GhostWork.create(applicationExecutor);
    }

    @Bean
    TrackedOperationResolver trackedOperationResolver() {
        return new TrackedOperationResolver();
    }

    @Bean
    TrackedOperationInvoker trackedOperationInvoker(
            GhostWork ghostWork,
            TrackedOperationResolver resolver
    ) {
        return new TrackedOperationInvoker(ghostWork, resolver);
    }

    @Bean
    TrackedOperationAspect trackedOperationAspect(
            TrackedOperationInvoker invoker
    ) {
        return new TrackedOperationAspect(invoker);
    }
}
```

Use `@TrackedOperation` on service methods:

```java
import io.nikitoo0os.GhostWork;
import io.nikitoo0os.annotation.TrackedOperation;
import org.springframework.stereotype.Service;

@Service
public class CustomerImportService {

    private final GhostWork ghostWork;

    public CustomerImportService(GhostWork ghostWork) {
        this.ghostWork = ghostWork;
    }

    @TrackedOperation("CustomerImport")
    public void importCustomers() {
        ghostWork.executor().submit(
                "LoadCustomers",
                this::loadCustomers
        );

        ghostWork.executor().submit(
                "ValidateCustomers",
                this::validateCustomers
        );
    }

    private void loadCustomers() {
        // load customers
    }

    private void validateCustomers() {
        // validate customers
    }
}
```

Tasks submitted inside the annotated method are tracked under the annotated operation.

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
target/ghostwork-0.1.0.jar
```

## Current Scope

GhostWork is an in-memory tracking library.

It does not currently provide:

* persistent storage
* distributed task tracking
* metrics export
* OpenTelemetry integration
* a Spring Boot auto-configuration starter
* a complete drop-in replacement for every `ExecutorService` method

`TrackingExecutorService` should be treated as a tracked submission facade around an existing executor.

## License

GhostWork is licensed under the Apache License, Version 2.0.
