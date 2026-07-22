# GhostWork

GhostWork is a lightweight Java library for tracking asynchronous operations and detecting tasks that continue running after their parent operation has already finished.

The project is designed as a small, explicit concurrency-tracking layer around Java tasks and executors. It records task lifecycle transitions, groups tasks by operation, and provides deterministic detection of ghost and long-running tasks.

> Status: active development
> Current version: `0.1.0`

## Maven

After publication to Maven Central, GhostWork can be added as:

```xml
<dependency>
    <groupId>io.github.nikitoo0os</groupId>
    <artifactId>ghostwork</artifactId>
    <version>0.1.0</version>
</dependency>
```

Spring AOP support is available through optional dependencies. In Spring Boot applications, add:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

## Publishing

Release publication uses Sonatype Central Portal.

Required local setup:

* verified Central Portal namespace: `io.github.nikitoo0os`
* Central Portal token in Maven `settings.xml` under server id `central`
* local GPG key available to `gpg`

Release command:

```powershell
mvn -P release clean deploy
```

The release profile attaches source and Javadoc jars, signs artifacts with GPG, and uploads the deployment bundle to Central Portal without auto-publishing.

## Problem

Asynchronous applications often submit work to an `ExecutorService` and lose direct visibility into the lifecycle of individual tasks.

This becomes a problem when:

* an operation finishes while one or more of its tasks are still running;
* a task runs significantly longer than expected;
* failures occur inside wrapped tasks;
* concurrent state transitions need to remain thread-safe;
* operational diagnostics require task-level information.

GhostWork introduces explicit domain objects for operations and tasks, stores them in a thread-safe registry, and provides detection mechanisms for abnormal execution states.

## Features

* Explicit operation lifecycle tracking
* Explicit task lifecycle tracking
* Thread-safe state transitions
* Thread-safe task and operation registry
* Runnable and Callable instrumentation
* ExecutorService integration
* Ghost task detection
* Long-running task detection
* Deterministic time-based testing through `Clock`
* Preservation of original task failures
* Immutable lifecycle snapshots
* Event listener API
* Periodic monitoring
* Read-only public views

## Requirements

* Java 21 or later
* Maven 3.8 or later

## Project Structure

```text
src/main/java/io/nikitoo0os
├── Detector.java
├── GhostTaskInfo.java
├── Main.java
├── TrackingExecutorService.java
├── entity
│   ├── Operation.java
│   ├── OperationSnapshot.java
│   ├── Registry.java
│   ├── Task.java
│   ├── TaskSnapshot.java
│   └── enums
│       ├── OperationState.java
│       └── TaskState.java
├── factory
│   ├── TrackingCallableFactory.java
│   └── TrackingRunnableFactory.java
└── wrap
    ├── WrappedCallable.java
    └── WrappedRunnable.java
```

## Core Concepts

### Operation

An `Operation` represents a logical unit of work that may contain one or more asynchronous tasks.

An operation starts in the `RUNNING` state and may transition to one of the following final states:

```text
RUNNING
├── COMPLETED
├── FAILED
└── TIMED_OUT
```

An operation cannot transition after reaching a final state.

### Task

A `Task` represents an individual unit of asynchronous work associated with an operation.

A task follows this lifecycle:

```text
CREATED
└── RUNNING
    ├── COMPLETED
    └── FAILED
```

Task state is stored in an immutable `TaskSnapshot` and updated atomically.

Each lifecycle transition records its corresponding timestamp:

* creation time;
* start time;
* finish time.

### Registry

`Registry` is the central in-memory storage for operations and tasks.

It provides:

* operation registration;
* task registration;
* lookup by identifier;
* lookup of tasks belonging to an operation.

The registry uses concurrent collections and supports access from multiple threads.

A task can only be registered if its parent operation is already present in the registry.

### Tracking wrappers

`WrappedRunnable` and `WrappedCallable` decorate user-provided tasks.

They are responsible for:

1. marking the task as running;
2. executing the original delegate;
3. marking the task as completed after successful execution;
4. marking the task as failed when execution throws;
5. rethrowing the original failure.

If the task state transition itself fails while handling an existing exception, the state transition failure is attached to the original exception as a suppressed exception.

This preserves the primary execution failure.

### Tracking factories

`TrackingRunnableFactory` and `TrackingCallableFactory` create tracked task wrappers.

Each factory:

* creates a `Task`;
* registers it in the `Registry`;
* wraps the original delegate;
* injects a shared `Clock` into the wrapper.

### TrackingExecutorService

`TrackingExecutorService` is a focused facade over an existing `ExecutorService`.

It currently supports tracked submission of:

* `Runnable`;
* `Callable<T>`.

Every submitted delegate is wrapped and registered before being passed to the underlying executor.

### Detector

`Detector` analyzes registered tasks.

It currently supports two detection modes.

#### Ghost task detection

A ghost task is a task that remains in the `RUNNING` state after its parent operation has finished.

```java
List<TaskView> ghostTasks =
        ghostWork.ghostTasks(operation.id());
```

#### Stuck task detection

A stuck task is a running task whose execution duration is strictly greater than a configured threshold.

```java
List<TaskView> stuckTasks =
        ghostWork.stuckTasks(
                operation.id(),
                Duration.ofSeconds(30)
        );
```

A task running for exactly the configured threshold is not considered stuck.

## Architecture

```text
                         ┌──────────────────────┐
                         │      Operation       │
                         │ logical unit of work │
                         └──────────┬───────────┘
                                    │ owns
                                    ▼
                         ┌──────────────────────┐
                         │         Task         │
                         │ lifecycle + timing   │
                         └──────────┬───────────┘
                                    │ stored in
                                    ▼
                         ┌──────────────────────┐
                         │       Registry       │
                         │ concurrent storage   │
                         └──────────┬───────────┘
                                    │ inspected by
                                    ▼
                         ┌──────────────────────┐
                         │       Detector       │
                         │ ghost/stuck analysis │
                         └──────────────────────┘
```

Task execution flow:

```text
User Runnable / Callable
           │
           ▼
TrackingRunnableFactory / TrackingCallableFactory
           │
           ├── creates Task
           ├── registers Task
           └── creates wrapper
                    │
                    ▼
          WrappedRunnable / WrappedCallable
                    │
                    ├── task.start(...)
                    ├── delegate execution
                    ├── task.complete(...)
                    └── task.fail(...)
```

## Time Management

Domain task objects do not access the system clock directly during lifecycle transitions.

Instead, time is supplied by infrastructure:

```text
Tracking Factory
      │
      │ Clock
      ▼
Task Wrapper
      │
      │ Instant.now(clock)
      ▼
Task lifecycle method
```

This design makes time-dependent behavior deterministic and testable.

Production code may use:

```java
Clock.systemUTC()
```

Tests may inject:

```java
Clock.fixed(
        Instant.parse("2026-01-01T10:00:00Z"),
        ZoneOffset.UTC
)
```

## Usage Example

```java
import io.nikitoo0os.GhostWork;
import io.nikitoo0os.OperationView;
import io.nikitoo0os.TaskView;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Example {

    public static void main(String[] args) throws Exception {
        GhostWork ghostWork =
                GhostWork.create(Executors.newFixedThreadPool(4));

        ghostWork.addEventListener(event -> {
            System.out.println(event.type());
        });
        
        ghostWork.call(
                "Data import",
                () -> {
                    ghostWork.executor()
                            .submit(
                                    "Import customers",
                                    () -> {
                                        // asynchronous work
                                    }
                            )
                            .get(1, TimeUnit.SECONDS);

                    return null;
                }
        );
        
        OperationView operation = ghostWork.operations().getFirst();
        List<TaskView> tasks = ghostWork.tasks(operation.id());

        System.out.println(operation.state());
        System.out.println(tasks.getFirst().state());

        ghostWork.executor().shutdown();
    }
}
```

## Thread Safety

GhostWork uses explicit concurrency primitives instead of relying on mutable unsynchronized state.

### Lifecycle transitions

Operation and task state are stored in `AtomicReference` instances.

Transitions use compare-and-set semantics:

```text
read current snapshot
        │
validate transition
        │
create immutable snapshot
        │
compareAndSet
```

If another thread changes the state first, the transition fails with an `IllegalStateException`.

### Registry storage

The registry uses:

* `ConcurrentHashMap`;
* `ConcurrentLinkedQueue`;
* immutable copies for returned task collections.

## Error Handling

GhostWork distinguishes between invalid input and invalid lifecycle transitions.

| Situation                                       | Exception                  |
| ----------------------------------------------- | -------------------------- |
| Required argument is `null`                     | `NullPointerException`     |
| Duration is zero or negative                    | `IllegalArgumentException` |
| Timestamp violates lifecycle ordering           | `IllegalArgumentException` |
| Entity cannot transition from its current state | `IllegalStateException`    |
| Entity cannot be found in the registry          | `NoSuchElementException`   |
| Duplicate registration                          | `IllegalStateException`    |

## Building the Project

Clone the repository:

```bash
git clone https://github.com/nikitoo0os/ghostwork.git
cd ghostwork
```

Run the test suite:

```bash
mvn clean test
```

Build the project:

```bash
mvn clean package
```

The generated JAR will be available in:

```text
target/ghostwork-0.1.0.jar
```

## Testing

The project uses JUnit 5.

The current test suite covers:

* operation lifecycle transitions;
* task lifecycle transitions;
* concurrent registry behavior;
* invalid state transitions;
* Runnable tracking;
* Callable tracking;
* exception propagation;
* suppressed lifecycle failures;
* ghost task detection;
* stuck task detection;
* deterministic timestamp handling.

Time-dependent tests use fixed clocks instead of wall-clock delays.

```java
Clock clock = Clock.fixed(
        Instant.parse("2026-01-01T10:00:00Z"),
        ZoneOffset.UTC
);
```

This keeps tests fast, repeatable, and independent of machine timing.

## Design Principles

GhostWork follows several core engineering principles:

* lifecycle state must be explicit;
* state transitions must be atomic;
* snapshots should be immutable;
* domain objects should not own infrastructure concerns;
* time should be injected where deterministic behavior matters;
* original failures must not be hidden by secondary failures;
* public behavior should be verified through tests;
* abstractions should be introduced only when they have a clear responsibility.

## Current Limitations

GhostWork is currently an early-stage in-memory library.

The current implementation does not yet provide:

* persistent storage;
* distributed task tracking;
* metrics export;
* annotation-based operation lifecycle orchestration;
* persistent storage;
* scheduled task scanning;
* integration with observability platforms;
* a complete implementation of the `ExecutorService` interface.

`TrackingExecutorService` should currently be treated as a focused tracked-submission facade rather than a drop-in replacement for every `ExecutorService` operation.

## Roadmap

* [x] Operation lifecycle model
* [x] Task lifecycle model
* [x] Immutable lifecycle snapshots
* [x] Thread-safe registry
* [x] Runnable tracking
* [x] Callable tracking
* [x] Executor submission facade
* [x] Ghost task detection
* [x] Long-running task detection
* [x] Deterministic clock injection
* [x] Operation lifecycle orchestration
* [x] Task cancellation tracking
* [x] Periodic detector execution
* [x] Event listener API
* [ ] Metrics integration
* [ ] Logging integration
* [ ] Complete ExecutorService decorator
* [ ] Public library release

## Contributing

GhostWork is currently developed as an educational and experimental concurrency project.

Contributions should preserve the following properties:

* deterministic tests;
* explicit lifecycle contracts;
* thread-safe state transitions;
* clear ownership of responsibilities;
* minimal hidden behavior;
* backward-compatible public APIs where practical.

Before submitting changes, run:

```bash
mvn clean test
```

## License

No license has been selected yet.

Until a license is added, the source code remains publicly visible but should not be assumed to grant permission for reuse, modification, or redistribution.
