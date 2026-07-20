# GhostWork Engineering and Learning Guidelines

This document defines the engineering standards, architectural principles, development workflow, and learning model used in the GhostWork project.

GhostWork is not developed only as a working Java library. It is also used as a structured environment for developing professional software engineering skills.

The target is not merely to complete individual features. The target is to build the ability to independently design, implement, test, review, and evolve production-grade Java systems at the **Middle+ and Senior developer level**.

---

## 1. Purpose

The project serves two parallel goals.

### Product goal

Build a lightweight, reliable, and extensible Java library for:

* tracking asynchronous operations;
* tracking task lifecycle transitions;
* detecting ghost tasks;
* detecting long-running tasks;
* integrating task tracking with Java executors;
* preserving correct behavior under concurrency.

### Learning goal

Develop professional competence in:

* Java Core;
* concurrency;
* object-oriented design;
* API design;
* software architecture;
* automated testing;
* refactoring;
* debugging;
* code review;
* technical decision-making;
* production-oriented engineering.

Every project decision should support both goals.

A technically working solution is not automatically considered a good solution. The implementation should also have clear ownership, explicit contracts, deterministic behavior, meaningful tests, and a defensible architectural rationale.

---

## 2. Target Engineering Level

The learning process is aimed at reaching the following capabilities.

### Middle-level expectations

The developer should be able to:

* implement a feature from a defined contract;
* understand and modify an existing codebase;
* write meaningful unit tests;
* identify invalid state transitions;
* use standard Java collections and concurrency primitives correctly;
* separate domain logic from infrastructure;
* debug compilation, runtime, and test failures;
* recognize duplicated responsibilities;
* perform local refactoring without changing behavior;
* explain why a chosen implementation works.

### Middle+ expectations

The developer should additionally be able to:

* define contracts before implementation;
* identify ownership of lifecycle and state;
* design small abstractions with clear responsibilities;
* recognize concurrency hazards;
* reason about atomicity and visibility;
* design deterministic time-dependent code;
* distinguish public API behavior from implementation details;
* identify architectural pressure before it becomes technical debt;
* review code in terms of invariants and failure modes;
* compare multiple valid designs and explain trade-offs.

### Senior-level direction

The long-term target is the ability to:

* model complex domains through explicit invariants;
* design systems that remain understandable as they grow;
* identify boundaries between domain, application, and infrastructure layers;
* reason about failure handling across multiple abstractions;
* make deliberate compatibility decisions;
* evolve APIs without unnecessary breakage;
* anticipate concurrency, observability, and operational concerns;
* explain architectural decisions to other engineers;
* reject unnecessary complexity;
* recognize when an abstraction is premature;
* guide implementation through contracts, tests, and review.

Senior-level development is not defined by the number of patterns used. It is defined by the quality of decisions, clarity of responsibility, awareness of trade-offs, and ability to keep a system maintainable under change.

---

## 3. Learning Model

The default learning method is based on guided problem solving.

The developer should remain the primary author of the code whenever the task has meaningful educational value.

The assistant acts as:

* technical mentor;
* architecture reviewer;
* code reviewer;
* debugging partner;
* source of targeted explanations;
* escalation point when a task becomes mechanically expensive or educationally repetitive.

### Default interaction model

For architectural and design tasks:

1. Clarify the responsibility.
2. Identify the owner of the responsibility.
3. Define invariants.
4. Define the public contract.
5. Consider failure modes.
6. Write or outline tests.
7. Implement the smallest valid solution.
8. Review the result.
9. Refactor only when the responsibility becomes clearer.

For syntax, build configuration, dependency setup, or mechanical test construction, direct answers are acceptable.

### Guidance policy

The assistant should normally avoid immediately providing a complete implementation when the task teaches an important engineering concept.

Instead, guidance should proceed through:

* focused questions;
* identification of invariants;
* small hints;
* API-level suggestions;
* review of the developer's implementation;
* explanation of trade-offs.

A complete implementation may be provided when:

* the developer explicitly requests it;
* the work is primarily mechanical;
* the concept has already been understood;
* repetition no longer creates meaningful learning value;
* the implementation is needed to unblock a broader architectural topic;
* the developer asks to compare their solution with a reference implementation.

### Avoiding over-assistance

The assistant should not remove all productive difficulty from the task.

Productive difficulty includes:

* deciding which class owns a responsibility;
* identifying required state transitions;
* designing method contracts;
* choosing appropriate tests;
* reasoning about edge cases;
* analyzing concurrency behavior.

Unproductive difficulty includes:

* remembering Maven XML syntax;
* reproducing repetitive test setup;
* resolving a known compiler flag;
* looking up an exact standard-library method;
* writing boilerplate after the design is already settled.

The goal is to preserve learning value without wasting effort on low-value mechanics.

---

## 4. Engineering Principles

### 4.1 Explicit responsibility

Every class should have a clearly stated responsibility.

A class should not exist only because a design pattern suggests it might be useful.

Before creating an abstraction, answer:

* What responsibility does it own?
* Why does the current owner not fit?
* What invariant does the abstraction protect?
* Who calls it?
* What does it expose?
* What does it deliberately hide?

If these questions cannot be answered precisely, the abstraction is probably premature.

### 4.2 Explicit lifecycle

Entities with lifecycle state must define:

* initial state;
* valid transitions;
* final states;
* invalid transitions;
* timestamps associated with transitions;
* behavior under concurrent transition attempts.

Lifecycle transitions should never be implied only through nullable fields.

Prefer an explicit state model such as:

```text
CREATED -> RUNNING -> COMPLETED
                   \-> FAILED
```

### 4.3 Invariants before implementation

Important behavior should be expressed as invariants.

Examples:

* a task belongs to exactly one operation;
* a task cannot start twice;
* a completed task cannot fail afterward;
* a task cannot finish before it starts;
* a task cannot be registered before its operation;
* an operation cannot leave a final state;
* only running tasks can be classified as stuck;
* only tasks of a finished operation can be classified as ghosts.

Implementation should preserve these invariants under normal and concurrent execution.

### 4.4 Clear ownership

State changes should have one clear owner.

Examples:

* `Task` owns task lifecycle validation;
* `Operation` owns operation lifecycle validation;
* `Registry` owns registration and lookup;
* wrappers own translating delegate execution into task lifecycle transitions;
* factories own tracked-task construction;
* `Detector` owns diagnostic classification;
* an executor facade owns delegation to an underlying executor.

Avoid splitting one responsibility across several classes unless the boundary is explicit.

### 4.5 Minimal hidden behavior

Public methods should do what their names and contracts imply.

Avoid methods that unexpectedly:

* register unrelated objects;
* complete operations;
* swallow exceptions;
* mutate global state;
* access the system clock;
* start background threads;
* perform logging as their primary result.

Hidden behavior makes code harder to test and harder to reason about.

### 4.6 Composition over inheritance

Prefer composition when adapting infrastructure.

For example, a tracking executor can delegate to an existing `ExecutorService` rather than reimplementing thread-pool behavior.

Inheritance should be used only when the subtype genuinely fulfills the complete behavioral contract of its parent type.

A class that implements only a subset of `ExecutorService` behavior should not claim to be a complete `ExecutorService` replacement.

### 4.7 Introduce abstractions under pressure

Do not create abstractions only for hypothetical future requirements.

An abstraction becomes justified when there is observable design pressure, such as:

* duplicated lifecycle orchestration;
* unclear responsibility;
* repeated validation;
* multiple callers needing the same contract;
* infrastructure leaking into the domain;
* difficult testing;
* incompatible implementations behind one stable concept.

Prefer the smallest abstraction that resolves current pressure without blocking foreseeable evolution.

---

## 5. Java Design Standards

### 5.1 Constructor injection

Required dependencies should be provided through constructors.

Examples:

* `Registry`;
* `Clock`;
* wrapped executor;
* task factories.

Constructor injection makes dependencies explicit and prevents partially initialized objects.

### 5.2 Null validation

Required references should be validated at the public boundary.

Prefer:

```java
this.registry = Objects.requireNonNull(
        registry,
        "Registry must not be null"
);
```

Use meaningful messages when they improve diagnosis.

### 5.3 Input validation

Use exception types consistently.

| Condition                       | Exception                  |
| ------------------------------- | -------------------------- |
| Required reference is `null`    | `NullPointerException`     |
| Value is structurally invalid   | `IllegalArgumentException` |
| Lifecycle transition is invalid | `IllegalStateException`    |
| Requested entity does not exist | `NoSuchElementException`   |
| Duplicate registration          | `IllegalStateException`    |

Validation should happen as close as possible to the boundary that owns the contract.

### 5.4 Immutability

Prefer immutable state representations.

Lifecycle snapshots should:

* be created completely;
* never be modified after creation;
* represent one valid state;
* be atomically replaceable.

Immutable snapshots simplify concurrency reasoning because readers never observe partially updated state.

### 5.5 Encapsulation

Internal mutable structures must not be exposed directly.

Return:

* immutable values;
* unmodifiable views;
* defensive copies;
* snapshots.

For example, registry queries should not expose an internal concurrent queue.

### 5.6 Naming

Names should describe domain meaning rather than implementation mechanics.

Prefer:

```java
detectStuckTasks()
registerOperation()
isRunningLongerThan()
parentOperation
finishedAt
```

Avoid vague names such as:

```java
process()
handle()
manager()
data
value
doWork()
```

Generic names are acceptable only when the context makes their meaning unambiguous.

### 5.7 Method size

Methods should remain focused on one conceptual operation.

A method should normally:

1. validate input;
2. read current state;
3. validate the transition;
4. build the result or next state;
5. perform the operation;
6. report failure.

Do not extract methods only to reduce line count. Extract when the resulting method has a meaningful responsibility or improves reasoning.

### 5.8 Comments

Comments should explain:

* why a non-obvious decision exists;
* what concurrency invariant is protected;
* why an unusual failure-handling strategy is required;
* why a particular trade-off was accepted.

Comments should not restate straightforward code.

Bad:

```java
// Set task state to running
task.start(now);
```

Better:

```java
// State is changed before delegate execution so detection can observe
// a task that blocks indefinitely inside the delegate.
task.start(now);
```

---

## 6. Concurrency Standards

Concurrency code must be designed around explicit guarantees, not intuition.

### 6.1 Questions required for concurrent code

Before accepting concurrent code, answer:

* Which state is shared?
* Which threads may access it?
* Is the operation required to be atomic?
* Is visibility guaranteed?
* Can two transitions race?
* What happens to the losing thread?
* Can readers observe an inconsistent combination of fields?
* Does the collection provide the required compound-operation semantics?

### 6.2 Atomic snapshots

Related lifecycle fields should be stored together in an immutable snapshot and replaced atomically.

For example:

```text
TaskSnapshot
├── state
├── startedAt
└── finishedAt
```

This prevents a reader from observing combinations such as:

```text
state = COMPLETED
finishedAt = null
```

### 6.3 Compare-and-set transitions

CAS-based transitions are appropriate when:

* the current state must be validated;
* the transition must occur only once;
* competing threads must not both succeed;
* blocking synchronization is unnecessary.

A typical transition flow is:

```text
read current snapshot
        |
validate source state
        |
create next immutable snapshot
        |
compareAndSet
        |
success or concurrent-transition failure
```

### 6.4 Concurrent collections

Using a concurrent collection does not automatically make every workflow atomic.

For compound operations, use atomic collection methods such as:

* `putIfAbsent`;
* `computeIfAbsent`;
* `remove(key, value)`;
* `replace`;
* `compute`.

Do not implement check-then-act logic with separate calls when correctness depends on atomicity.

Unsafe conceptually:

```java
if (!map.containsKey(key)) {
    map.put(key, value);
}
```

Preferred:

```java
map.putIfAbsent(key, value);
```

### 6.5 Synchronization

Do not reject `synchronized` categorically.

Use it when it provides the clearest correct solution for a critical section.

However:

* do not synchronize an entire object without need;
* do not hold locks while executing user code;
* do not mix multiple locking strategies without clear ownership;
* document lock ordering when multiple locks exist.

### 6.6 User-provided code

User delegates such as `Runnable` and `Callable` are untrusted from the infrastructure perspective.

They may:

* block indefinitely;
* throw checked or unchecked exceptions;
* throw an `Error`;
* interrupt the thread;
* mutate external state;
* submit additional work.

Infrastructure should preserve its own invariants without hiding the original failure.

---

## 7. Time Management

### 7.1 Time is a dependency

Code whose behavior depends on the current time should receive a `Clock` or explicit `Instant`.

Avoid direct calls to:

```java
Instant.now()
System.currentTimeMillis()
```

inside logic that needs deterministic testing.

### 7.2 Responsibility boundary

Infrastructure may obtain the current time:

```java
Instant now = Instant.now(clock);
```

Domain objects should receive that time as transition data:

```java
task.start(now);
task.complete(now);
```

This keeps the domain deterministic.

### 7.3 Time invariants

Time-based lifecycle code should validate:

* start time is present when required;
* finish time is not before start time;
* target inspection time is not before start time;
* duration thresholds are positive;
* boundary semantics are explicitly defined.

For GhostWork, stuck detection currently uses strict comparison:

```text
running duration > threshold
```

Therefore, equality with the threshold is not classified as stuck.

### 7.4 Testing time

Time-dependent tests should use:

```java
Clock.fixed(...)
```

or explicit `Instant` values.

Avoid:

```java
Thread.sleep(...)
```

unless sleeping itself is the behavior under test.

Tests based on wall-clock delays are slower and nondeterministic.

---

## 8. Exception and Failure Handling

### 8.1 Preserve the primary failure

When user code throws, the original throwable is the primary failure.

If lifecycle bookkeeping also fails, the bookkeeping failure must not replace the original one.

Preferred strategy:

1. catch the original throwable;
2. attempt the failure-state transition;
3. attach transition failure with `addSuppressed`;
4. rethrow the original throwable.

Conceptually:

```java
try {
    delegate.run();
} catch (Throwable originalFailure) {
    try {
        task.fail(now);
    } catch (Throwable lifecycleFailure) {
        originalFailure.addSuppressed(lifecycleFailure);
    }

    throw originalFailure;
}
```

This preserves diagnostic causality.

### 8.2 Catching `Throwable`

Catching `Throwable` is normally too broad in application code.

It may be justified at a framework boundary that must record the result of arbitrary user-provided execution.

When catching `Throwable`:

* do not swallow it;
* do not convert it without necessity;
* preserve interruption semantics;
* preserve the original type where possible;
* keep the catch scope minimal.

### 8.3 Do not use exceptions for normal branching

Exceptions should indicate contract violation or execution failure.

Do not rely on exceptions as the normal mechanism for checking entity existence or state where an explicit query is more appropriate.

### 8.4 Error messages

Messages should contain actionable information.

Prefer:

```text
The task cannot switch from state COMPLETED to state FAILED
```

over:

```text
Invalid state
```

---

## 9. Testing Standards

Tests are part of the design, not a final verification step.

### 9.1 Test public behavior

Prefer testing observable contracts:

* returned values;
* state transitions;
* timestamps;
* thrown exceptions;
* registry contents;
* delegate invocation;
* failure propagation.

Avoid testing private implementation details.

### 9.2 Arrange, Act, Assert

Tests should have a visible structure:

```java
// arrange

// act

// assert
```

Comments are optional when the phases are already obvious.

### 9.3 Test naming

Test names should describe the contract.

Preferred:

```java
finishedOperationWithRunningTaskShouldBeDetectedAsGhost()
taskRunningExactlyThresholdShouldNotBeDetectedAsStuck()
duplicateTaskRegistrationShouldThrow()
```

Avoid:

```java
testDetector()
test1()
shouldWork()
```

### 9.4 Required test categories

For every meaningful public behavior, consider:

* happy path;
* invalid input;
* boundary value;
* invalid state;
* duplicate action;
* missing entity;
* failure propagation;
* concurrency race;
* empty result;
* multiple matching entities.

Not every method requires all categories, but the selection must be deliberate.

### 9.5 Boundary testing

Boundary semantics must be tested explicitly.

For duration logic:

```text
threshold - 1 unit
threshold
threshold + 1 unit
```

For state transitions:

```text
valid source state
already-final state
competing transition
```

### 9.6 Deterministic tests

Tests should not depend on:

* scheduler timing;
* machine speed;
* test execution order;
* current date;
* global mutable state;
* arbitrary sleeps.

### 9.7 Concurrency tests

Concurrency tests should use coordination primitives such as:

* `CountDownLatch`;
* `CyclicBarrier`;
* `Phaser`;
* `ExecutorService`;
* atomic counters.

A concurrency test should intentionally create the race being tested rather than merely running code in several threads and hoping the race occurs.

### 9.8 Assertion quality

Prefer assertions that express the contract precisely.

Use:

```java
assertSame(expected, actual);
assertEquals(expectedState, task.getState());
assertThrows(IllegalStateException.class, action);
assertTrue(result.isEmpty());
```

Avoid assertions that are weaker than the behavior being tested.

### 9.9 Test duplication

Some setup duplication is acceptable when it keeps tests readable.

Introduce fixtures, builders, or helper methods when setup:

* becomes error-prone;
* obscures the scenario;
* is repeated extensively;
* needs one canonical construction path.

Do not create a complex test framework for a small test suite.

---

## 10. API Design Standards

### 10.1 Contract first

Before adding a public method, define:

* inputs;
* output;
* side effects;
* exceptions;
* thread-safety guarantees;
* ownership implications;
* boundary behavior.

### 10.2 Focused APIs

Public methods should expose domain operations, not internal storage mechanics.

Prefer:

```java
findTasksByOperation(operationId)
detectGhostTasks(operationId)
```

over exposing internal maps or queues.

### 10.3 Return values

Return the most specific useful type.

Use:

```java
List<Task>
```

instead of:

```java
Collection<Object>
```

Return empty collections rather than `null`.

### 10.4 Compatibility

Before changing an existing public method, consider:

* whether callers will break;
* whether an overload can preserve compatibility;
* whether the old contract was incorrect;
* whether the project version permits breaking changes;
* whether migration should be documented.

During early development, breaking changes may be acceptable, but they should remain intentional.

### 10.5 Facade honesty

An API should not imply a stronger contract than it implements.

For example, a class named `TrackingExecutorService` may be interpreted as a complete `ExecutorService`.

If it only supports tracked submission, either:

* clearly document that limitation;
* rename it to reflect facade semantics;
* or eventually implement the full interface contract.

---

## 11. Architectural Review Checklist

Before accepting a feature, review it at several levels.

### Responsibility

* Does every class have one clear reason to change?
* Is the responsibility owned by the correct layer?
* Is orchestration mixed with domain validation?

### Domain

* Are states explicit?
* Are transitions legal and complete?
* Are invariants enforced by the owning entity?
* Can invalid objects be created?

### Concurrency

* Is shared state identified?
* Are transitions atomic?
* Can readers observe partial state?
* Are compound collection operations safe?
* What happens during races?

### Time

* Is the current time injected?
* Are temporal boundaries defined?
* Can tests control time?

### Failure handling

* Is the original failure preserved?
* Are secondary failures suppressed correctly?
* Are exceptions typed consistently?
* Is any failure swallowed?

### Testing

* Is the happy path tested?
* Are invalid inputs tested?
* Are boundaries tested?
* Are final states tested?
* Are concurrency contracts tested where necessary?
* Are tests deterministic?

### API

* Is the method name precise?
* Are side effects explicit?
* Is the return type appropriate?
* Does the API overpromise?
* Is the behavior documented?

### Complexity

* Is the abstraction currently necessary?
* Can the same responsibility be expressed more simply?
* Is complexity solving a real problem?
* Is future extensibility being purchased at an unjustified current cost?

---

## 12. Development Workflow

### Step 1: Define the problem

Describe the problem without mentioning a specific implementation.

Example:

> An operation may finish while one of its asynchronous tasks remains running. The system must identify such tasks.

### Step 2: Define terminology

Establish precise terms:

* operation;
* task;
* ghost task;
* stuck task;
* final state;
* threshold.

Ambiguous terminology creates ambiguous APIs.

### Step 3: Identify invariants

Write the rules that must always hold.

### Step 4: Assign ownership

Decide which class enforces each rule.

### Step 5: Define the contract

Specify method signatures, return values, exceptions, and boundary semantics.

### Step 6: Write tests

Start with the most important behavioral cases.

Tests may be written before or alongside implementation, but the contract should exist before the final implementation.

### Step 7: Implement minimally

Write the smallest implementation that satisfies the current contract.

Do not add speculative extension points.

### Step 8: Run the complete test suite

Use:

```bash
mvn clean test
```

A feature is not complete while unrelated tests are failing.

### Step 9: Review

Review:

* naming;
* duplication;
* responsibility;
* failure modes;
* concurrency;
* test quality;
* public API impact.

### Step 10: Refactor

Refactor only with passing tests.

A refactoring should improve structure without changing externally observable behavior.

### Step 11: Commit

A commit should represent one coherent change.

Examples:

```text
Add deterministic stuck task detection
Cover Detector boundary behavior
Inject Clock into task tracking lifecycle
Preserve delegate failures during task state updates
```

Avoid vague messages:

```text
fix
changes
update
work
```

---

## 13. Code Review Model

Code review should not focus only on syntax or formatting.

Review should proceed in this order:

1. Correctness
2. Contract
3. Invariants
4. Failure behavior
5. Concurrency
6. Responsibility
7. Test coverage
8. Readability
9. Style

### Review questions

* What assumption does this code make?
* Where is that assumption enforced?
* What happens when two threads execute this simultaneously?
* What is the failure mode?
* Can the state become inconsistent?
* Does this method own this responsibility?
* Is this test proving the contract or the implementation?
* Is the abstraction necessary now?
* What would make this code difficult to change later?

### Review feedback levels

Feedback should distinguish:

#### Blocking

The code is incorrect, unsafe, breaks a contract, or introduces significant architectural damage.

#### Important

The code works but has a maintainability, testing, ownership, or API problem that should be resolved.

#### Suggestion

The change would improve clarity or style but is not required for correctness.

This prevents cosmetic feedback from being treated as equivalent to correctness issues.

---

## 14. Refactoring Policy

Refactoring is not a substitute for understanding.

Before refactoring:

* tests must pass;
* current behavior must be understood;
* the code smell must be named;
* the expected structural improvement must be clear.

Valid refactoring motivations include:

* duplicated responsibility;
* unclear ownership;
* overly broad class;
* repeated validation;
* difficult test setup;
* infrastructure leaking into domain logic;
* public API inconsistency;
* excessive coupling.

Avoid refactoring only because:

* another design looks more sophisticated;
* a pattern could be applied;
* a future feature might need it;
* the code is not aesthetically perfect.

---

## 15. Documentation Standards

### README.md

The README is user-facing project documentation.

It should explain:

* the problem;
* the library purpose;
* features;
* architecture overview;
* usage;
* build instructions;
* testing;
* current limitations;
* roadmap.

### SKILLS.md

This document defines:

* engineering standards;
* learning methodology;
* review criteria;
* growth objectives;
* collaboration rules.

### Architecture documentation

Significant design decisions should eventually be documented separately.

Recommended location:

```text
docs/architecture/
```

Possible records:

```text
001-atomic-lifecycle-snapshots.md
002-clock-injection.md
003-task-wrapper-failure-semantics.md
004-tracking-executor-boundary.md
```

Each decision record should contain:

* context;
* considered options;
* decision;
* consequences;
* known limitations.

---

## 16. Progress Evaluation

Progress should be evaluated through demonstrated capability, not only completed files or test counts.

### Evidence of growth

Strong evidence includes the ability to:

* independently identify invariants;
* predict edge cases before tests fail;
* explain why a race condition exists;
* choose between CAS and locking;
* reject an unnecessary abstraction;
* identify misplaced responsibility;
* design a deterministic test;
* explain failure propagation;
* review unfamiliar code;
* propose migration-safe API changes;
* justify a technical decision with trade-offs.

### Warning signs

The learning process should be adjusted if the developer:

* copies implementations without understanding them;
* cannot explain state transitions;
* relies on trial and error for concurrency;
* adds patterns without responsibility;
* writes only happy-path tests;
* treats passing tests as proof of good architecture;
* avoids changing code because existing tests pass;
* changes public APIs without considering callers;
* depends entirely on assistant-generated solutions.

### Periodic self-review

After each significant milestone, answer:

1. What responsibility was introduced?
2. Which invariant does it protect?
3. What alternative design was possible?
4. Why was the selected design preferred?
5. What failure modes are covered?
6. What remains weak or incomplete?
7. What concept can now be implemented independently?
8. What concept still requires guidance?

---

## 17. Assistant Collaboration Rules

The assistant should adapt its level of help to the educational value of the task.

### For architecture

The assistant should:

* ask focused design questions;
* challenge unclear ownership;
* identify missing invariants;
* discuss alternatives;
* avoid presenting one design as universally correct;
* explain trade-offs;
* review the developer's reasoning.

### For implementation

The assistant should:

* provide hints first when learning value is high;
* review submitted code precisely;
* identify correctness issues before style issues;
* avoid rewriting entire classes unnecessarily;
* provide full code when explicitly requested or justified.

### For debugging

The assistant should:

* identify the actual failing layer;
* distinguish compiler, build, test, runtime, and design failures;
* explain the diagnostic evidence;
* propose the smallest corrective change;
* avoid unrelated refactoring during incident resolution.

### For tests

The assistant should:

* help identify behavior categories;
* highlight boundaries and failure paths;
* support deterministic test design;
* provide complete repetitive test suites when the developer understands the underlying contract and requests implementation help.

### For direct answers

Direct answers are preferred for:

* Java syntax;
* Maven configuration;
* IDE configuration;
* standard-library usage;
* command-line operations;
* repetitive boilerplate;
* known mechanical transformations.

### Explanation requirement

When a complete solution is provided, the important reasoning should still be explained:

* why the solution belongs in that class;
* what invariant it protects;
* what alternatives exist;
* what assumptions it makes;
* what should be tested.

---

## 18. GhostWork-Specific Rules

### Operation

* An operation starts in `RUNNING`.
* An operation may finish as `COMPLETED`, `FAILED`, or `TIMED_OUT`.
* Final states are terminal.
* Concurrent finalization must allow only one successful transition.

### Task

* A task starts in `CREATED`.
* A task may transition from `CREATED` to `RUNNING`.
* A running task may transition to `COMPLETED` or `FAILED`.
* Final task states are terminal.
* Start and finish timestamps must form a valid chronology.

### Registry

* An operation must be registered before its tasks.
* Duplicate identifiers are rejected.
* Lookups of missing entities fail explicitly.
* Internal mutable collections are not exposed.
* Registration must remain thread-safe.

### Wrappers

* Task start is recorded before delegate execution.
* Success results in completion.
* Delegate failure results in task failure.
* Original failures are rethrown.
* Lifecycle failures must not hide delegate failures.
* Wrapper behavior must remain transparent to the delegate contract.

### Detector

* Ghost detection applies to tasks belonging to a finished operation.
* Only running tasks are ghosts.
* Stuck detection applies only to running tasks.
* The stuck threshold must be positive.
* A duration exactly equal to the threshold is not stuck.
* Detection should return data rather than perform presentation or logging.

### Executor integration

* The underlying executor remains responsible for thread management.
* Tracking infrastructure decorates task submission.
* Registration occurs before execution is delegated.
* Generic `Callable<T>` results and exceptions must be preserved.
* The tracking facade must not claim unsupported `ExecutorService` behavior.

---

## 19. Definition of Done

A feature is complete when:

* its responsibility is clear;
* its contract is explicit;
* invariants are enforced;
* invalid inputs are handled;
* lifecycle failures are considered;
* concurrency behavior is understood;
* time-dependent behavior is deterministic;
* meaningful tests exist;
* all tests pass;
* naming is clear;
* documentation is updated when public behavior changes;
* no unnecessary abstraction was introduced;
* the developer can explain the implementation and its trade-offs.

Passing tests alone is necessary but not sufficient.

---

## 20. Long-Term Direction

GhostWork should evolve incrementally toward a production-quality architecture while remaining small enough to understand fully.

Potential future learning areas include:

* complete `ExecutorService` decoration;
* operation orchestration;
* task cancellation;
* interruption semantics;
* scheduled detection;
* event listeners;
* observability;
* metrics;
* structured logging;
* persistence boundaries;
* distributed task identity;
* API compatibility;
* performance testing;
* stress testing;
* module design;
* library publication;
* semantic versioning.

Each new area should be introduced only after the current layer is stable and understood.

The purpose is not to add every possible feature. The purpose is to progressively encounter and solve engineering problems that develop production-level judgment.

---

## Final Principle

The main measure of progress is not how much code has been written.

The measure is whether the developer can increasingly answer, independently and precisely:

* What problem are we solving?
* Who owns this responsibility?
* What invariants must hold?
* What can fail?
* What happens under concurrency?
* How is the behavior tested?
* Why is this design preferable to the alternatives?
* How can the system evolve without losing clarity?

The project succeeds when these questions become a normal part of implementation rather than an external review step.
