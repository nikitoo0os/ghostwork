# GhostWork Architecture

## 1. Document Purpose

This document defines the target architecture of GhostWork.

It describes:

* the product vision;
* architectural boundaries;
* module responsibilities;
* dependency direction;
* public API design;
* operation and task lifecycle models;
* complete `ExecutorService` decoration;
* operation context propagation;
* implicit operation creation;
* annotation-based tracking;
* asynchronous completion semantics;
* cancellation and rejection behavior;
* diagnostic classification;
* observability extension points;
* registry retention;
* implementation phases;
* architectural constraints.

This document describes the intended product architecture rather than only the current codebase.

The current implementation may temporarily differ from the target structure while GhostWork evolves incrementally.

## 1.1 Implementation Status (`0.4.x`)

The framework-independent core currently implements:

* the `CREATED -> SUBMITTED -> RUNNING -> terminal` task lifecycle;
* atomic task and operation transitions;
* operation context propagation and restoration;
* implicit operations;
* the complete standard `ExecutorService` contract;
* cancellation, rejection, interruption, and bulk-operation tracking;
* ghost and stuck diagnostics;
* configurable in-memory retention by TTL and completed-operation count.

Spring AOP integration is published separately as `ghostwork-spring`.
The opt-in Spring MVC dashboard is published separately as
`ghostwork-dashboard-spring`. Scheduled executor decoration, persistence, and
distributed tracing remain future work.

---

# 2. Product Vision

GhostWork is a Java library for tracking asynchronous work and detecting execution anomalies.

The library should provide:

* explicit operation lifecycle tracking;
* explicit task lifecycle tracking;
* transparent integration with Java executors;
* automatic operation creation;
* annotation-based operation boundaries;
* operation context propagation between threads;
* detection of ghost tasks;
* detection of long-running tasks;
* deterministic time handling;
* extensibility for metrics, logging, events, and framework integrations.

The main product goal is to make asynchronous execution observable without requiring application developers to manually create and register internal tracking entities.

A typical Spring application should be able to use GhostWork as follows:

```java
@Service
public class CustomerImportService {

    private final ExecutorService executor;

    public CustomerImportService(ExecutorService executor) {
        this.executor = executor;
    }

    @TrackedOperation("CustomerImport")
    public void importCustomers() {
        executor.submit(this::loadCustomers);
        executor.submit(this::validateCustomers);
    }
}
```

GhostWork should automatically:

1. intercept the annotated method;
2. create and register an operation;
3. bind the operation to the current execution context;
4. associate submitted tasks with that operation;
5. propagate the operation context into executor threads;
6. track task lifecycle transitions;
7. complete or fail the operation according to its completion policy;
8. identify tasks that remain active after operation completion;
9. expose immutable diagnostic information.

Application code should not normally contain:

```java
Operation operation = new Operation("CustomerImport");
registry.registerOperation(operation);
```

Manual domain entity construction is an internal implementation concern rather than the primary public API.

---

# 3. Core Architectural Principle

GhostWork should make asynchronous execution visible without taking ownership of application business logic.

The architecture is based on:

```text
explicit lifecycle model
        +
operation context
        +
context propagation
        +
complete ExecutorService decoration
        +
optional framework integrations
        +
diagnostic analysis
```

Framework annotations are integration conveniences.

The actual foundation of the product is the framework-independent lifecycle, orchestration, registry, and context model.

---

# 4. Architectural Goals

## 4.1 Framework independence

The core library must not depend on:

* Spring Framework;
* Spring Boot;
* Micronaut;
* Quarkus;
* Jakarta CDI;
* Reactor;
* Micrometer;
* OpenTelemetry;
* application-specific infrastructure.

Framework-specific behavior belongs in optional integration modules.

## 4.2 Low integration cost

A user should be able to integrate GhostWork with minimal configuration.

For Spring Boot applications, adding the starter and annotating public service methods should be sufficient for common use cases.

## 4.3 Transparent execution

Tracked execution must preserve the original contracts of:

* `Runnable`;
* `Callable<T>`;
* `Future<T>`;
* `Executor`;
* `ExecutorService`;
* `ScheduledExecutorService`, when supported.

GhostWork must preserve:

* return values;
* exceptions;
* cancellation;
* interruption;
* rejection;
* timeouts;
* shutdown behavior;
* bulk execution behavior.

## 4.4 Explicit lifecycle semantics

Operation and task states must be represented explicitly.

Invalid transitions must fail predictably.

## 4.5 Deterministic behavior

Time-dependent logic must use:

* injected `Clock`;
* explicit `Instant`;
* explicit `Duration`.

Tests must not depend on wall-clock time or arbitrary sleeping.

## 4.6 Thread safety

Lifecycle transitions, registration, context propagation, cancellation, completion, and detection must remain correct under concurrent execution.

## 4.7 Small public API

Users should interact with a compact and stable API.

Internal registries, wrappers, mutable entities, snapshots, and orchestration components should not become accidental public contracts.

## 4.8 Extensibility

Future integrations should be possible without modifying the core domain model.

Potential integrations include:

* Spring AOP;
* Jakarta CDI interceptors;
* Micronaut AOP;
* Quarkus;
* Reactor;
* Kotlin coroutines;
* Micrometer;
* OpenTelemetry;
* structured logging;
* Spring Boot Actuator.

---

# 5. Non-Goals

GhostWork is not intended to become:

* a workflow engine;
* a distributed task queue;
* a job scheduler;
* a transaction manager;
* a tracing backend;
* a persistence platform;
* a replacement for `ExecutorService`;
* a business process engine;
* a distributed orchestration platform.

GhostWork may integrate with such systems, but its responsibility remains tracking and diagnosing asynchronous execution.

---

# 6. High-Level Architecture

```text
Application code
       |
       v
Framework integration / Public API
       |
       v
Operation orchestration
       |
       v
Operation context
       |
       v
Executor decoration and task tracking
       |
       v
Core lifecycle model and registry
       |
       v
Detection and observability adapters
```

The dependency direction must always point toward the core:

```text
spring-boot-starter
        |
        v
spring-boot-autoconfigure
        |
        v
spring integration
        |
        v
executor integration
        |
        v
core
```

The core module must never depend on an integration module.

---

# 7. Target Repository Structure

The final project should use a multi-module Maven structure:

```text
ghostwork
├── pom.xml
├── README.md
├── LICENSE
├── CHANGELOG.md
│
├── docs
│   ├── ARCHITECTURE.md
│   └── adr
│       ├── 0001-atomic-lifecycle-snapshots.md
│       ├── 0002-clock-injection.md
│       ├── 0003-complete-executor-decoration.md
│       ├── 0004-operation-context-propagation.md
│       ├── 0005-implicit-operations.md
│       ├── 0006-tracked-operation-annotation.md
│       └── 0007-task-cancellation-model.md
│
├── ghostwork-core
├── ghostwork-executor
├── ghostwork-spring
├── ghostwork-spring-boot-autoconfigure
├── ghostwork-spring-boot-starter
├── ghostwork-observability
├── ghostwork-micrometer
└── ghostwork-test
```

The project should not be split into all modules immediately.

The initial implementation may remain a single Maven module while package boundaries and contracts are stabilized.

Physical module separation should follow architectural stability rather than precede it.

---

# 8. Module Responsibilities

## 8.1 `ghostwork-core`

The core module contains framework-independent domain and application abstractions.

Suggested package structure:

```text
io.nikitoo0os.ghostwork
├── api
├── operation
├── task
├── registry
├── context
├── detection
├── event
└── internal
```

### Responsibilities

* operation lifecycle;
* task lifecycle;
* immutable lifecycle snapshots;
* operation and task identifiers;
* operation origin;
* registry contracts;
* in-memory registry implementation;
* operation context abstraction;
* operation orchestration;
* implicit operation orchestration;
* ghost task detection;
* stuck task detection;
* immutable diagnostic DTOs;
* framework-independent public API;
* optional domain event contracts.

### Prohibited dependencies

`ghostwork-core` must not depend on:

* Spring;
* Spring Boot;
* Reactor;
* Micrometer;
* logging implementations;
* application code;
* concrete executor implementations.

---

## 8.2 `ghostwork-executor`

This module integrates GhostWork with Java concurrency APIs.

Suggested package structure:

```text
io.nikitoo0os.ghostwork.executor
├── TrackingExecutor.java
├── TrackingExecutorService.java
├── TrackingScheduledExecutorService.java
├── TrackingFuture.java
├── TaskNameResolver.java
├── wrapper
│   ├── TrackingRunnable.java
│   └── TrackingCallable.java
└── internal
```

### Responsibilities

* complete `ExecutorService` decoration;
* task creation;
* task registration;
* implicit operation creation;
* operation context capture;
* context restoration in worker threads;
* task lifecycle tracking;
* task-name resolution;
* `Future` decoration;
* cancellation tracking;
* rejection tracking;
* interruption preservation;
* shutdown delegation;
* bulk method support.

### Architectural constraint

`TrackingExecutorService` must implement the complete `ExecutorService` contract.

It must not expose itself as a complete decorator while supporting only selected methods.

---

## 8.3 `ghostwork-spring`

This module integrates GhostWork with Spring Framework.

Suggested package structure:

```text
io.nikitoo0os.ghostwork.spring
├── annotation
│   └── TrackedOperation.java
├── aop
│   └── TrackedOperationAspect.java
├── naming
│   ├── OperationNameResolver.java
│   └── DefaultOperationNameResolver.java
└── async
    └── SpringAsyncResultAdapter.java
```

### Responsibilities

* define `@TrackedOperation`;
* intercept annotated method execution;
* resolve operation names;
* map method execution to operation lifecycle;
* support nested operation propagation;
* adapt supported asynchronous return values;
* preserve original exceptions;
* delegate lifecycle behavior to the core API.

The Spring module must not duplicate core lifecycle logic.

---

## 8.4 `ghostwork-spring-boot-autoconfigure`

This module provides Spring Boot auto-configuration.

### Responsibilities

* register GhostWork core beans;
* register `TrackedOperationAspect`;
* configure `Clock`;
* configure registry implementation;
* configure operation context;
* configure detection;
* decorate eligible executor beans;
* bind configuration properties;
* activate components conditionally;
* allow application-defined bean overrides.

Possible configuration:

```yaml
ghostwork:
  enabled: true

  executor:
    decorate-existing: true

  detection:
    enabled: true
    interval: 10s
    stuck-threshold: 30s
```

---

## 8.5 `ghostwork-spring-boot-starter`

The starter should contain minimal implementation code.

Its responsibility is to provide the correct dependency set for Spring Boot applications.

```xml
<dependency>
    <groupId>io.nikitoo0os</groupId>
    <artifactId>ghostwork-spring-boot-starter</artifactId>
    <version>${ghostwork.version}</version>
</dependency>
```

---

## 8.6 `ghostwork-observability`

This optional module provides generic observability adapters.

Possible responsibilities:

* lifecycle event publication;
* diagnostic listeners;
* structured logging integration;
* health information;
* operation statistics;
* task statistics.

The core may expose contracts, but it must not depend on a concrete observability implementation.

---

## 8.7 `ghostwork-micrometer`

This optional module maps GhostWork state to Micrometer metrics.

Potential metrics:

```text
ghostwork.operations.active
ghostwork.operations.completed
ghostwork.operations.failed
ghostwork.operations.timed_out

ghostwork.tasks.submitted
ghostwork.tasks.running
ghostwork.tasks.completed
ghostwork.tasks.failed
ghostwork.tasks.cancelled
ghostwork.tasks.rejected
ghostwork.tasks.ghost
ghostwork.tasks.stuck

ghostwork.task.duration
ghostwork.operation.duration
```

---

## 8.8 `ghostwork-test`

This module provides testing utilities for applications using GhostWork.

Potential contents:

* fixed clocks;
* test registries;
* deterministic executors;
* operation fixtures;
* task fixtures;
* context test utilities;
* GhostWork-specific assertions.

---

# 9. Public API Design

Users should not normally instantiate mutable domain entities directly.

The public API should provide orchestration abstractions.

Possible public types:

```text
GhostWork
OperationHandle
OperationScope
OperationInfo
TaskInfo
GhostTaskDiagnostic
StuckTaskDiagnostic
DetectionService
```

Possible internal types:

```text
DefaultGhostWork
DefaultOperationHandle
DefaultOperationScope
OperationEntity
TaskEntity
OperationSnapshot
TaskSnapshot
InMemoryRegistry
TrackingRunnable
TrackingCallable
```

## 9.1 Programmatic operation API

Basic scoped use:

```java
try (OperationScope scope =
             ghostWork.openOperation("CustomerImport")) {

    importCustomers();
    scope.complete();
}
```

Convenience API for `Runnable`:

```java
ghostWork.run(
        "CustomerImport",
        this::importCustomers
);
```

Convenience API for returning values:

```java
CustomerImportResult result = ghostWork.call(
        "CustomerImport",
        this::importCustomers
);
```

## 9.2 Information exposure

Users should receive immutable information objects rather than mutable entities.

Example:

```java
public record OperationInfo(
        UUID id,
        String name,
        OperationState state,
        OperationOrigin origin,
        Instant createdAt,
        Instant finishedAt
) {
}
```

---

# 10. Operation Model

An operation represents a logical unit of work.

## 10.1 Operation states

```java
public enum OperationState {
    RUNNING,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    CANCELLED
}
```

The initial implementation may introduce `CANCELLED` later if operation cancellation is not yet supported, but the architecture reserves it as a final state.

## 10.2 Final operation states

The following states are final:

```text
COMPLETED
FAILED
TIMED_OUT
CANCELLED
```

## 10.3 Operation origin

Operations must retain information about how they were created.

```java
public enum OperationOrigin {
    EXPLICIT,
    ANNOTATION,
    IMPLICIT
}
```

### `EXPLICIT`

Created through the programmatic GhostWork API.

### `ANNOTATION`

Created by a framework integration such as `@TrackedOperation`.

### `IMPLICIT`

Created automatically by `TrackingExecutorService` because no active operation existed during task submission.

## 10.4 Required operation information

An operation should contain:

* operation identifier;
* operation name;
* operation origin;
* current state;
* creation timestamp;
* completion timestamp;
* optional parent operation identifier;
* optional failure information;
* optional metadata.

## 10.5 Operation invariants

* a new operation starts in `RUNNING`;
* an operation can enter a final state only once;
* final states are terminal;
* completion time cannot precede creation time;
* operation identity remains stable;
* operation origin remains stable;
* concurrent finalization allows exactly one successful transition;
* child task state does not automatically determine operation state.

---

# 11. Task Model

A task represents one submitted executable unit associated with one operation.

## 11.1 Task states

```java
public enum TaskState {
    CREATED,
    SUBMITTED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REJECTED
}
```

## 11.2 Task lifecycle

```text
CREATED
├── SUBMITTED
│   ├── RUNNING
│   │   ├── COMPLETED
│   │   ├── FAILED
│   │   └── CANCELLED
│   └── CANCELLED
└── REJECTED
```

## 11.3 State semantics

### `CREATED`

The tracked task exists inside GhostWork but has not yet been accepted by the wrapped executor.

### `SUBMITTED`

The wrapped executor accepted the task for execution.

The task may still be waiting in a queue.

### `RUNNING`

A worker thread started executing the delegate.

### `COMPLETED`

The delegate completed normally.

### `FAILED`

The delegate terminated by throwing an exception or `Error`.

### `CANCELLED`

Execution was successfully cancelled through the corresponding `Future` or executor lifecycle behavior.

### `REJECTED`

The wrapped executor rejected the task before execution.

## 11.4 Required task information

A task should contain:

* task identifier;
* task name;
* parent operation identifier;
* current state;
* creation timestamp;
* submission timestamp;
* start timestamp;
* finish timestamp;
* cancellation timestamp;
* whether interruption was requested;
* optional failure information;
* optional executing thread information;
* optional metadata.

## 11.5 Task invariants

* every task belongs to exactly one operation;
* a new task starts in `CREATED`;
* a task can be submitted only once;
* a rejected task never becomes running;
* a cancelled task never becomes completed or failed afterward;
* a completed task cannot fail afterward;
* a failed task cannot complete afterward;
* final transitions are atomic;
* finish time cannot precede start time;
* start time cannot precede creation time.

---

# 12. Complete ExecutorService Decoration

GhostWork will provide a full decorator:

```java
public final class TrackingExecutorService
        implements ExecutorService
```

The wrapped executor remains responsible for:

* thread creation;
* queueing;
* scheduling;
* worker management;
* shutdown;
* termination;
* rejection policy.

GhostWork adds tracking without reimplementing thread-pool mechanics.

## 12.1 Required methods

The decorator must correctly support:

```java
void execute(Runnable command);

Future<?> submit(Runnable task);

<T> Future<T> submit(
        Runnable task,
        T result
);

<T> Future<T> submit(
        Callable<T> task
);

<T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks
);

<T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks,
        long timeout,
        TimeUnit unit
);

<T> T invokeAny(
        Collection<? extends Callable<T>> tasks
);

<T> T invokeAny(
        Collection<? extends Callable<T>> tasks,
        long timeout,
        TimeUnit unit
);

void shutdown();

List<Runnable> shutdownNow();

boolean isShutdown();

boolean isTerminated();

boolean awaitTermination(
        long timeout,
        TimeUnit unit
);
```

## 12.2 Behavioral requirements

The decorator must preserve:

* returned values;
* delegate exceptions;
* `ExecutionException`;
* cancellation behavior;
* timeout behavior;
* rejection behavior;
* interruption semantics;
* shutdown semantics;
* bulk execution semantics;
* executor ownership.

## 12.3 Extended task naming API

GhostWork may provide additional overloads:

```java
Future<?> submit(
        String taskName,
        Runnable task
);

<T> Future<T> submit(
        String taskName,
        Callable<T> task
);
```

Standard `ExecutorService` methods must remain fully supported.

When no explicit name is provided, a `TaskNameResolver` generates one.

Suggested resolution order:

1. explicitly supplied task name;
2. name exposed by a named-task interface;
3. configured custom resolver;
4. delegate class name;
5. generated fallback identifier.

## 12.4 Same-thread task steps

Some tracked work must remain on the caller thread because database
transactions, request state, or security context are thread-bound.

```java
void runTask(
        String taskName,
        Runnable task
);

<T> T callTask(
        String taskName,
        Callable<T> task
) throws Exception;
```

These methods require an active operation, register a normal task, publish the
same lifecycle events, and preserve the original return value or failure. They
must not submit work to the delegate executor.

---

# 13. Implicit Operations

When `TrackingExecutorService` receives a task and no operation is active in `OperationContext`, GhostWork must create an implicit operation.

This is confirmed product behavior.

## 13.1 Single-task methods

For:

```text
execute
submit
```

one implicit operation is created for one submitted task.

```text
Implicit operation
        |
        └── submitted task
```

The implicit operation is finalized according to the task result:

```text
Task COMPLETED -> Operation COMPLETED
Task FAILED    -> Operation FAILED
Task CANCELLED -> Operation CANCELLED
Task REJECTED  -> Operation FAILED or dedicated rejection mapping
```

The exact mapping for a rejected root task should be finalized before implementation. The initial recommendation is to finalize the implicit operation as `FAILED` and retain task state `REJECTED`.

## 13.2 Bulk methods

For:

```text
invokeAll
invokeAny
```

one implicit operation is created for the complete executor invocation.

All tasks submitted by that invocation belong to the same operation.

```text
ImplicitOperation[invokeAll]
├── task-1
├── task-2
└── task-3
```

This models the user-visible executor call as one logical operation.

## 13.3 Implicit operation naming

Suggested format:

```text
ImplicitOperation[taskName]
```

Examples:

```text
ImplicitOperation[PriceService.recalculatePrices]
ImplicitOperation[invokeAll]
ImplicitOperation[task-7f31c1]
```

## 13.4 Context propagation

An implicit operation must be propagated into the submitted task.

This allows nested submissions from inside the task to join the same operation unless a new operation boundary is explicitly created.

---

# 14. Task Registration and Rejection

Task registration must occur before delegate submission so no executing task can exist outside the GhostWork registry.

Conceptual flow:

```text
resolve or create operation
        |
        v
create task in CREATED
        |
        v
register task
        |
        v
submit wrapped delegate
        |
        +--> accepted -> SUBMITTED
        |
        +--> rejected -> REJECTED
```

When the wrapped executor throws `RejectedExecutionException`:

* the task transitions to `REJECTED`;
* the rejection timestamp is recorded;
* the original exception is propagated unchanged;
* the task never transitions to `SUBMITTED` or `RUNNING`;
* an implicit parent operation is finalized consistently.

---

# 15. Future Decoration and Cancellation

A wrapper around `Runnable` or `Callable` cannot observe calls to:

```java
future.cancel(true);
```

Therefore, `TrackingExecutorService` must return decorated `Future` instances.

```java
public final class TrackingFuture<T>
        implements Future<T> {

    private final Future<T> delegate;
    private final TaskHandle task;
    private final Clock clock;

    @Override
    public boolean cancel(
            boolean mayInterruptIfRunning
    ) {
        boolean cancelled =
                delegate.cancel(mayInterruptIfRunning);

        if (cancelled) {
            task.cancel(
                    Instant.now(clock),
                    mayInterruptIfRunning
            );
        }

        return cancelled;
    }
}
```

The final implementation must correctly delegate:

* `cancel`;
* `isCancelled`;
* `isDone`;
* `get`;
* timed `get`.

## 15.1 Cancellation semantics

Cancellation is a dedicated lifecycle result.

It must not be represented as:

* successful completion;
* generic failure.

A successfully cancelled task enters `CANCELLED`.

## 15.2 Cancellation before execution

```text
SUBMITTED -> CANCELLED
```

The task was accepted but never started.

## 15.3 Cancellation during execution

```text
RUNNING -> CANCELLED
```

The cancellation request may optionally interrupt the worker thread.

## 15.4 Completion and cancellation race

The following transitions may compete:

```text
RUNNING -> COMPLETED
RUNNING -> FAILED
RUNNING -> CANCELLED
```

Exactly one final transition may succeed.

The transition must be atomic.

## 15.5 Parent operation behavior

Cancelling a child task does not automatically cancel or fail an explicit or annotation-created parent operation.

Initial rule:

```text
Task cancellation does not automatically change parent operation state.
```

For an implicit operation created for one task, the operation may be finalized as `CANCELLED` because the operation exists solely for that task.

---

# 16. Interruption Semantics

GhostWork must preserve Java interruption behavior.

Rules:

* GhostWork must not silently clear the interrupted flag;
* original interruption-related exceptions must be preserved;
* a successful `Future.cancel(true)` records that interruption was requested;
* interruption request does not prove that the task stopped immediately;
* a delegate may ignore interruption;
* a cancelled task remains `CANCELLED` even if delegate cleanup continues;
* lifecycle bookkeeping must not hide the original execution outcome.

An `InterruptedException` thrown without a recorded successful cancellation is treated as delegate failure unless a more specific policy is introduced later.

---

# 17. Operation Context

Automatic task association requires a current operation context.

## 17.1 Core abstraction

```java
public interface OperationContext {

    Optional<OperationHandle> current();

    OperationScope push(
            OperationHandle operation
    );

    OperationContextSnapshot capture();

    OperationScope restore(
            OperationContextSnapshot snapshot
    );
}
```

## 17.2 Requirements

The context must:

* support nested operations;
* restore previous state after scope closure;
* capture context in the submitting thread;
* restore context in worker threads;
* avoid worker-thread context leaks;
* remain correct when delegates fail;
* remain framework-independent.

## 17.3 Stack semantics

A single:

```java
ThreadLocal<OperationHandle>
```

is insufficient because nested operations must restore their parent.

The initial implementation should use stack semantics:

```java
ThreadLocal<Deque<OperationHandle>>
```

Conceptually:

```text
Parent operation
       |
       v
push child operation
       |
       v
execute child
       |
       v
pop child operation
       |
       v
restore parent operation
```

## 17.4 Context propagation

At submission time:

```text
capture current context
        |
        v
create tracked wrapper
        |
        v
submit wrapper
```

At execution time:

```text
capture worker's previous context
        |
        v
restore submitted context
        |
        v
execute delegate
        |
        v
restore worker's previous context
```

A worker thread must not retain operation context after task completion.

---

# 18. Annotation-Based Tracking

Spring users should be able to declare operation boundaries with:

```java
@TrackedOperation
```

The annotation name is intentionally not `@Operation`.

Reasons:

* `Operation` is already a domain concept;
* imports would become confusing;
* `TrackedOperation` clearly describes behavior;
* the annotation remains framework-facing rather than domain-facing;
* the name supports future related annotations.

## 18.1 Proposed contract

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TrackedOperation {

    String value() default "";

    CompletionMode completion()
            default CompletionMode.METHOD_RETURN;

    OperationPropagation propagation()
            default OperationPropagation.REQUIRED;
}
```

## 18.2 Usage

Default operation name:

```java
@TrackedOperation
public void recalculatePrices() {
}
```

Resolved name:

```text
PriceService.recalculatePrices
```

Explicit name:

```java
@TrackedOperation("NightlyPriceRecalculation")
public void recalculatePrices() {
}
```

Asynchronous result:

```java
@TrackedOperation(
        value = "CustomerImport",
        completion = CompletionMode.ASYNC_RESULT
)
public CompletableFuture<Integer> importCustomers() {
    return importService.runAsync();
}
```

---

# 19. TrackedOperation Aspect

The Spring integration should implement:

```java
@Aspect
public final class TrackedOperationAspect
```

Conceptual behavior:

```java
@Around("@annotation(annotation)")
public Object trackOperation(
        ProceedingJoinPoint joinPoint,
        TrackedOperation annotation
) throws Throwable {

    OperationHandle operation =
            operationManager.resolveOrStart(
                    joinPoint,
                    annotation
            );

    try (OperationScope ignored =
                 operationContext.push(operation)) {

        Object result = joinPoint.proceed();

        completionHandler.handleSuccess(
                operation,
                result,
                annotation.completion()
        );

        return result;
    } catch (Throwable failure) {
        completionHandler.handleFailure(
                operation,
                failure
        );

        throw failure;
    }
}
```

The aspect must:

* resolve propagation;
* create an `ANNOTATION` operation when needed;
* bind operation context;
* execute the intercepted method;
* process synchronous or asynchronous completion;
* preserve original exceptions;
* restore previous context;
* avoid lifecycle duplication.

---

# 20. Operation Propagation

Nested tracked methods require explicit propagation semantics.

## 20.1 Initial modes

```java
public enum OperationPropagation {
    REQUIRED,
    REQUIRES_NEW
}
```

## 20.2 `REQUIRED`

* reuse the current operation when one exists;
* create a new operation when none exists.

This is the default.

Example:

```java
@TrackedOperation
public void parent() {
    childService.child();
}
```

```java
@TrackedOperation
public void child() {
}
```

With `REQUIRED`, both methods participate in the same logical operation.

## 20.3 `REQUIRES_NEW`

* always create a new operation;
* temporarily make it current;
* restore the previous operation afterward;
* record the previous operation as parent when appropriate.

```java
@TrackedOperation(
        propagation = OperationPropagation.REQUIRES_NEW
)
public void child() {
}
```

## 20.4 Future propagation modes

Potential future modes:

```text
MANDATORY
SUPPORTS
NEVER
NOT_SUPPORTED
```

These must not be introduced without concrete use cases.

---

# 21. Completion Semantics

The library must explicitly define when an annotation-created operation is complete.

```java
public enum CompletionMode {
    METHOD_RETURN,
    ASYNC_RESULT,
    MANUAL
}
```

## 21.1 `METHOD_RETURN`

The operation completes when the intercepted method returns successfully.

The operation fails when the method throws.

This is the default.

```java
@TrackedOperation
public void importCustomers() {
}
```

If the method submits asynchronous tasks and then returns, the operation becomes final even while those tasks may still be active.

Those active tasks are intentionally detectable as ghost tasks.

## 21.2 `ASYNC_RESULT`

The operation remains active after method return.

It completes when the returned asynchronous result terminates.

Initial support:

```text
CompletionStage
CompletableFuture
```

Rules:

* normal completion produces `COMPLETED`;
* exceptional completion produces `FAILED`;
* cancellation mapping must be explicit;
* unsupported return types must fail predictably;
* caller-thread context is removed after method return;
* captured operation identity is retained by callbacks and tracked tasks.

Future adapters may support:

```text
Reactor Mono
Reactor Flux
Kotlin Deferred
Mutiny Uni
```

## 21.3 `MANUAL`

The operation is not automatically finalized by method return.

Application code must complete it through the programmatic API.

This is an advanced mode.

The library must eventually provide protection against permanently open manual operations.

---

# 22. Spring AOP Constraints

Annotation support uses Spring proxy interception.

Therefore:

* the target object must be a Spring-managed bean;
* invocation must pass through the Spring proxy;
* self-invocation does not trigger the aspect;
* private methods are not intercepted through standard proxy-based Spring AOP;
* final methods may prevent interception;
* final classes may prevent class-based proxying;
* annotated boundaries should normally be public methods.

Example that does not trigger tracking:

```java
@Service
public class ImportService {

    public void start() {
        importCustomers();
    }

    @TrackedOperation
    public void importCustomers() {
    }
}
```

The call:

```java
this.importCustomers();
```

bypasses the proxy.

Recommended structure:

```java
@Service
public class ImportScheduler {

    private final ImportService importService;

    public void run() {
        importService.importCustomers();
    }
}
```

---

# 23. Spring Boot Executor Decoration

Spring Boot integration should be able to decorate existing `ExecutorService` beans.

Potential modes:

```text
DEDICATED
SELECTED
ALL_ELIGIBLE
DISABLED
```

Example:

```yaml
ghostwork:
  executor:
    mode: selected
    bean-names:
      - applicationExecutor
      - importExecutor
```

Auto-configuration must avoid:

* decorating one executor more than once;
* circular bean creation;
* decorating GhostWork's internal executors;
* unexpectedly replacing infrastructure executors;
* changing shutdown ownership;
* breaking bean identity assumptions.

The wrapped executor remains owned by the application or Spring container.

---

# 24. Ghost Task Detection

A task is a ghost when:

* its parent operation is in any final state;
* the task remains active.

## 24.1 Final parent states

```text
COMPLETED
FAILED
TIMED_OUT
CANCELLED
```

## 24.2 Active task states

```text
SUBMITTED
RUNNING
```

## 24.3 Classification

```text
COMPLETED operation + RUNNING task = ghost
FAILED operation + RUNNING task    = ghost
TIMED_OUT operation + RUNNING task = ghost
CANCELLED operation + RUNNING task = ghost
```

The reason the operation ended does not change ghost classification.

The essential condition is:

```java
operation.isFinished() && task.isActive()
```

A task in `CANCELLED`, `FAILED`, `COMPLETED`, or `REJECTED` is not active and therefore is not a ghost.

---

# 25. Stuck Task Detection

A task is stuck when:

* it is in `RUNNING`;
* its running duration is strictly greater than the configured threshold.

Boundary contract:

```text
running duration > threshold
```

A task running for exactly the threshold is not classified as stuck.

Tasks in `SUBMITTED` may later receive a separate queued-too-long diagnostic, but they are not initially classified as stuck.

---

# 26. Diagnostic Output

Detection should return immutable DTOs.

Example:

```java
public record GhostTaskDiagnostic(
        UUID taskId,
        String taskName,
        UUID operationId,
        String operationName,
        OperationState operationState,
        OperationOrigin operationOrigin,
        TaskState taskState,
        Instant submittedAt,
        Instant startedAt,
        Duration activeDuration
) {
}
```

Detector responsibilities:

* inspect state;
* classify anomalies;
* return immutable diagnostic information.

Detector must not:

* print to console;
* send alerts;
* cancel tasks;
* change operation state;
* mutate tasks;
* write directly to a metrics backend.

---

# 27. Automatic Detection

A future scheduler may periodically execute detection.

```text
scheduled trigger
       |
       v
scan registry
       |
       v
detect ghost and stuck tasks
       |
       v
publish diagnostic events
       |
       v
logging / metrics / alert adapters
```

The scheduler must support:

* configurable interval;
* configurable thresholds;
* controlled shutdown;
* exception isolation;
* prevention of overlapping scans;
* optional batching;
* bounded registry scanning.

Automatic scanning should not belong to the domain model.

---

# 28. Failure Handling

GhostWork must preserve the original application failure.

When delegate execution fails and lifecycle recording also fails:

1. preserve the delegate failure as primary;
2. attach lifecycle failure as suppressed;
3. rethrow the original failure.

Conceptually:

```java
try {
    delegate.run();
} catch (Throwable originalFailure) {
    try {
        task.fail(now);
    } catch (Throwable lifecycleFailure) {
        originalFailure.addSuppressed(
                lifecycleFailure
        );
    }

    throw originalFailure;
}
```

The same rule applies to:

* annotated methods;
* asynchronous result callbacks;
* cancellation handling;
* registry bookkeeping;
* context restoration.

Infrastructure failures must not silently replace business failures.

---

# 29. Failure Information

The core should avoid retaining arbitrary throwable instances indefinitely by default.

A serializable representation may be used:

```java
public record FailureInfo(
        String type,
        String message,
        String stackTrace
) {
}
```

The final design must consider:

* memory usage;
* stack-trace size;
* sensitive information;
* retention duration;
* serialization compatibility.

---

# 30. Events

The core may publish immutable lifecycle events.

Potential operation events:

```text
OperationStarted
OperationCompleted
OperationFailed
OperationTimedOut
OperationCancelled
```

Potential task events:

```text
TaskCreated
TaskSubmitted
TaskStarted
TaskCompleted
TaskFailed
TaskCancelled
TaskRejected
```

Potential diagnostic events:

```text
GhostTaskDetected
StuckTaskDetected
```

Event principles:

* events are immutable;
* listener failure does not corrupt lifecycle state;
* listener failure does not hide application failure;
* publication semantics must be explicit;
* duplicate diagnostic event behavior must be defined;
* event ordering guarantees must be documented.

Events may be introduced after lifecycle contracts stabilize.

---

# 31. Registry Lifecycle and Retention

An in-memory registry cannot retain all operations and tasks forever.

The architecture must support retention and cleanup.

Potential policies:

* retain active entities;
* retain completed entities for a configured duration;
* retain the latest configured number of operations;
* remove operations only after all associated tasks become final;
* export diagnostics before eviction;
* allow custom registry implementations.

Example configuration:

```yaml
ghostwork:
  registry:
    retention: 1h
    max-completed-operations: 10000
```

A completed operation must not be removed while active child tasks still require ghost detection.

Safe eviction conditions:

* operation is final;
* all associated tasks are final;
* retention period elapsed;
* required events or diagnostics were published.

---

# 32. Metadata and Correlation

Operations and tasks may carry immutable metadata.

Possible metadata:

```text
tenantId
requestId
traceId
userId
jobId
source
environment
```

Metadata rules:

* no mutable internal maps should be exposed;
* sensitive data must not be captured automatically;
* method arguments and return values must not be recorded by default;
* contributors may be supplied by integrations;
* metadata cardinality must be considered for metrics;
* metadata keys should have stable naming rules.

---

# 33. Observability Integration

GhostWork should complement tracing and monitoring systems rather than replace them.

Potential integrations:

* Micrometer;
* OpenTelemetry;
* structured logging;
* Spring Boot Actuator;
* custom alerting systems.

A future Actuator endpoint may expose:

```text
/actuator/ghostwork
```

Possible information:

* active operations;
* running tasks;
* ghost tasks;
* stuck tasks;
* cancelled tasks;
* rejected tasks;
* registry statistics.

Security and privacy must be considered before exposing names, metadata, or failure information.

---

# 34. Package Naming and Visibility

The root package should be:

```text
io.nikitoo0os.ghostwork
```

Suggested package boundaries:

```text
io.nikitoo0os.ghostwork.api
io.nikitoo0os.ghostwork.operation
io.nikitoo0os.ghostwork.task
io.nikitoo0os.ghostwork.registry
io.nikitoo0os.ghostwork.context
io.nikitoo0os.ghostwork.detection
io.nikitoo0os.ghostwork.executor
io.nikitoo0os.ghostwork.spring
io.nikitoo0os.ghostwork.internal
```

Classes inside `internal` packages are not public contracts.

A future Java module descriptor may export only supported packages.

```java
module io.nikitoo0os.ghostwork.core {
    exports io.nikitoo0os.ghostwork.api;
}
```

The exact export set should remain minimal.

---

# 35. Compatibility and Versioning

GhostWork should follow semantic versioning after public API stabilization.

```text
MAJOR.MINOR.PATCH
```

* `MAJOR`: incompatible public API changes;
* `MINOR`: backward-compatible functionality;
* `PATCH`: backward-compatible fixes.

Before `1.0.0`, public APIs may evolve more rapidly, but breaking changes must remain deliberate and documented.

Compatibility includes:

* package names;
* class names;
* method signatures;
* lifecycle semantics;
* exception behavior;
* annotation attributes;
* configuration properties;
* event contracts;
* metric names;
* operation and task state meanings.

---

# 36. Security and Privacy

GhostWork must avoid collecting sensitive information by default.

It should not automatically retain:

* method arguments;
* return values;
* credentials;
* tokens;
* personal data;
* request payloads;
* arbitrary object representations.

Operation and task names may appear in logs and metrics.

Users must avoid sensitive or high-cardinality names.

Metadata capture must be explicit and configurable.

---

# 37. Performance Principles

Tracking should introduce predictable and measurable overhead.

Important concerns:

* immutable snapshot allocation;
* registry contention;
* wrapper allocation;
* context capture cost;
* `Future` decoration;
* stack-trace retention;
* detector scan complexity;
* event publication;
* cleanup cost;
* high-cardinality metrics.

Optimization must be based on evidence.

A future benchmark module may use JMH for:

* lifecycle transition throughput;
* task registration throughput;
* operation context overhead;
* executor decoration overhead;
* `Future` decoration overhead;
* detection scan performance.

Correctness has higher priority than premature micro-optimization.

---

# 38. Testing Strategy

## 38.1 Core tests

* operation lifecycle transitions;
* task lifecycle transitions;
* invalid chronology;
* atomic finalization;
* duplicate registration;
* implicit operation creation;
* ghost detection;
* stuck detection;
* operation origin;
* context stack behavior;
* context cleanup.

## 38.2 Executor tests

* `execute`;
* all `submit` overloads;
* `invokeAll`;
* timed `invokeAll`;
* `invokeAny`;
* timed `invokeAny`;
* shutdown;
* `shutdownNow`;
* termination;
* result preservation;
* exception preservation;
* rejection;
* cancellation before execution;
* cancellation during execution;
* cancellation/completion races;
* interruption;
* nested submission;
* context propagation;
* worker context restoration;
* implicit operations;
* bulk implicit operations.

## 38.3 Spring tests

* `@TrackedOperation` interception;
* default operation naming;
* explicit naming;
* operation origin;
* successful completion;
* method failure;
* `REQUIRED`;
* `REQUIRES_NEW`;
* parent restoration;
* asynchronous completion;
* asynchronous failure;
* asynchronous cancellation;
* unsupported async return types;
* proxy limitations.

## 38.4 Spring Boot tests

* conditional auto-configuration;
* property binding;
* executor decoration;
* bean override behavior;
* disabled configuration;
* selected executor mode;
* duplicate decoration protection;
* application context startup.

## 38.5 Concurrency tests

Tests should intentionally create races using:

* `CountDownLatch`;
* `CyclicBarrier`;
* `Phaser`;
* dedicated executors;
* atomic counters.

Important races:

* completion versus cancellation;
* completion versus failure;
* duplicate operation finalization;
* duplicate task start;
* concurrent registration;
* context restoration after failure.

## 38.6 Stress testing

The project may later use:

* repeated race tests;
* JCStress;
* JMH;
* long-running integration tests.

---

# 39. Implementation Roadmap

## Phase 1: Stabilize the current model

* preserve existing functionality;
* finalize current operation lifecycle;
* finalize current task lifecycle;
* stabilize registry behavior;
* stabilize detector behavior;
* maintain deterministic time;
* keep tests passing.

## Phase 2: Introduce complete task states

Add:

```text
SUBMITTED
CANCELLED
REJECTED
```

Update snapshots, transitions, validation, and tests.

## Phase 3: Package restructuring

* move packages under `io.nikitoo0os.ghostwork`;
* establish core, executor, context, and detection boundaries;
* define public and internal packages.

## Phase 4: Programmatic API

* introduce `GhostWork`;
* introduce `OperationHandle`;
* introduce `OperationScope`;
* hide normal manual entity registration;
* create immutable information DTOs.

## Phase 5: Operation context

* implement thread-local stack;
* implement capture;
* implement restoration;
* support nesting;
* test context cleanup.

## Phase 6: Implicit operations

* create implicit operations when context is absent;
* add `OperationOrigin`;
* implement single-task implicit operations;
* implement bulk-call implicit operations;
* define implicit finalization mapping.

## Phase 7: Complete ExecutorService decoration

* implement every `ExecutorService` method;
* preserve all contracts;
* add task naming;
* propagate context;
* implement rejection handling;
* implement `TrackingFuture`;
* implement cancellation;
* implement interruption semantics;
* test bulk methods.

## Phase 8: Spring integration

* introduce `@TrackedOperation`;
* implement `TrackedOperationAspect`;
* implement default naming;
* implement `REQUIRED`;
* implement `REQUIRES_NEW`;
* document proxy limitations.

## Phase 9: Asynchronous results

* support `CompletionStage`;
* support `CompletableFuture`;
* define cancellation mapping;
* test callback races;
* preserve context identity.

## Phase 10: Spring Boot starter

* add auto-configuration;
* add configuration properties;
* add executor bean decoration;
* provide starter dependency;
* protect against duplicate decoration.

## Phase 11: Observability

* add lifecycle events;
* add diagnostic events;
* add Micrometer integration;
* add optional Actuator support;
* introduce retention policies.

## Phase 12: Production hardening

* stress testing;
* JMH benchmarks;
* compatibility review;
* security review;
* memory-retention review;
* complete documentation;
* public release preparation.

---

# 40. Confirmed Architectural Decisions

The following decisions are currently fixed.

## 40.1 Complete decorator

GhostWork will provide a full `ExecutorService` decorator rather than a partial facade.

## 40.2 Annotation name

The framework annotation is named:

```java
@TrackedOperation
```

The name `@GhostOperation` is not used.

## 40.3 Automatic operation creation

When a task is submitted without an active operation, GhostWork creates an implicit operation.

## 40.4 Operation origin

Operations retain one of:

```text
EXPLICIT
ANNOTATION
IMPLICIT
```

## 40.5 Ghost classification

A task is a ghost when its parent operation is in any final state while the task remains active.

## 40.6 Complete task states

The target task state model includes:

```text
CREATED
SUBMITTED
RUNNING
COMPLETED
FAILED
CANCELLED
REJECTED
```

## 40.7 Cancellation

Cancellation is a dedicated task state.

It is not mapped to successful completion or generic failure.

## 40.8 Rejection

Rejected executor submissions transition the task to `REJECTED`.

The original `RejectedExecutionException` is preserved.

## 40.9 Child cancellation

Cancelling a child task does not automatically change an explicit or annotation-created parent operation.

## 40.10 Context propagation

Operation context must be captured at submission and restored during worker execution.

## 40.11 Framework independence

Annotation support is optional.

The core must remain usable in plain Java applications.

---

# 41. Remaining Architectural Questions

The following details remain intentionally open.

## 41.1 Root-task rejection mapping

When the only task of an implicit operation is rejected, should the operation become:

```text
FAILED
CANCELLED
a future REJECTED operation state
```

Initial recommendation:

```text
Operation FAILED
Task REJECTED
```

This avoids adding a narrowly specialized operation state prematurely.

## 41.2 Async-result cancellation

When a `CompletionStage`-based operation is cancelled, should the operation become:

```text
CANCELLED
FAILED
```

Recommended direction:

```text
CANCELLED
```

when cancellation can be reliably identified.

## 41.3 Bulk operation completion

For `invokeAny`, some tasks may be cancelled after one task succeeds.

The implicit operation should probably become `COMPLETED` when the executor call returns successfully, while internally cancelled tasks remain `CANCELLED`.

This requires exact contract tests.

## 41.4 Operation cancellation API

The architecture reserves `OperationState.CANCELLED`, but the programmatic operation cancellation contract still requires design.

## 41.5 Manual operation timeout

`CompletionMode.MANUAL` needs a protection policy against permanently open operations.

---

# 42. Architectural Decision Priority

When implementation choices conflict, use the following priority:

1. correctness;
2. lifecycle integrity;
3. preservation of Java executor contracts;
4. preservation of application failures;
5. thread safety;
6. diagnostic accuracy;
7. API clarity;
8. framework independence;
9. maintainability;
10. performance;
11. convenience.

Convenience must not override lifecycle correctness or executor compatibility.

---

# 43. Definition of Architectural Success

GhostWork succeeds when an application developer can use ordinary Java or framework APIs while the library can reliably answer:

* which operation created this task;
* whether the operation was explicit, annotation-created, or implicit;
* when the task was created;
* when the executor accepted it;
* when execution started;
* whether it completed;
* whether it failed;
* whether it was cancelled;
* whether it was rejected;
* whether it survived its parent operation;
* whether it has been running too long;
* how operation context moved between threads;
* whether lifecycle information remains internally consistent.

The application should receive this visibility without surrendering ownership of its business logic, executor configuration, or failure behavior.
