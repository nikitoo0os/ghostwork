package io.nikitoo0os;

import io.nikitoo0os.context.OperationContext;
import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.entity.enums.TaskState;
import io.nikitoo0os.event.GhostWorkEvent;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.event.GhostWorkEventType;
import io.nikitoo0os.factory.TrackingCallable;
import io.nikitoo0os.factory.TrackingCallableFactory;
import io.nikitoo0os.factory.TrackingRunnable;
import io.nikitoo0os.factory.TrackingRunnableFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class TrackingExecutorService implements ExecutorService {
    private final ExecutorService delegate;
    private final Registry registry;
    private final TrackingRunnableFactory runnableFactory;
    private final TrackingCallableFactory callableFactory;
    private final Clock clock;
    private final GhostWorkEventPublisher eventPublisher;
    private final ConcurrentMap<Runnable, QueuedTask> queuedTasks =
            new ConcurrentHashMap<>();

    public TrackingExecutorService(
            ExecutorService delegate,
            TrackingRunnableFactory runnableFactory,
            TrackingCallableFactory callableFactory,
            Clock clock,
            GhostWorkEventPublisher eventPublisher,
            Registry registry
    ) {
        this.delegate = Objects.requireNonNull(
                delegate,
                "Delegate executor must not be null"
        );
        this.runnableFactory = Objects.requireNonNull(
                runnableFactory,
                "Runnable factory must not be null"
        );
        this.callableFactory = Objects.requireNonNull(
                callableFactory,
                "Callable factory must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher,
                "Event publisher must not be null"
        );
        this.registry = Objects.requireNonNull(
                registry,
                "Registry must not be null"
        );
        if (runnableFactory.registry() != registry
                || callableFactory.registry() != registry) {
            throw new IllegalArgumentException(
                    "Executor and tracking factories must share one Registry"
            );
        }
    }

    public TrackingExecutorService(
            ExecutorService delegate,
            TrackingRunnableFactory runnableFactory,
            TrackingCallableFactory callableFactory,
            Clock clock,
            GhostWorkEventPublisher eventPublisher
    ) {
        this(
                delegate,
                runnableFactory,
                callableFactory,
                clock,
                eventPublisher,
                runnableFactory.registry()
        );
    }

    public TrackingExecutorService(
            ExecutorService delegate,
            TrackingRunnableFactory runnableFactory,
            TrackingCallableFactory callableFactory,
            Clock clock
    ) {
        this(
                delegate,
                runnableFactory,
                callableFactory,
                clock,
                new GhostWorkEventPublisher()
        );
    }

    public TrackingExecutorService(
            ExecutorService delegate,
            TrackingRunnableFactory runnableFactory,
            TrackingCallableFactory callableFactory
    ) {
        this(
                delegate,
                runnableFactory,
                callableFactory,
                Clock.systemUTC()
        );
    }

    public Future<?> submit(
            Operation operation,
            String taskName,
            Runnable runnable
    ) {
        TrackingRunnable trackingRunnable =
                runnableFactory.wrap(operation, taskName, runnable);
        SubmissionGate gate = new SubmissionGate();
        Future<?> delegateFuture;
        try {
            delegateFuture = delegate.submit(
                    gate.wrap(trackingRunnable.runnable())
            );
        } catch (RuntimeException exception) {
            reject(trackingRunnable);
            throw exception;
        }
        markSubmitted(trackingRunnable.task());
        gate.open();
        trackQueued(delegateFuture, trackingRunnable.task(), false);

        return new TrackingFuture<>(
                delegateFuture,
                trackingRunnable.task(),
                clock,
                eventPublisher
        );
    }

    public <T> Future<T> submit(
            Operation operation,
            String taskName,
            Callable<T> callable
    ) {
        TrackingCallable<T> trackingCallable =
                callableFactory.wrap(operation, taskName, callable);
        SubmissionGate gate = new SubmissionGate();
        Future<T> delegateFuture;
        try {
            delegateFuture = delegate.submit(
                    gate.wrap(trackingCallable.callable())
            );
        } catch (RuntimeException exception) {
            reject(trackingCallable);
            throw exception;
        }
        markSubmitted(trackingCallable.task());
        gate.open();
        trackQueued(delegateFuture, trackingCallable.task(), false);

        return new TrackingFuture<>(
                delegateFuture,
                trackingCallable.task(),
                clock,
                eventPublisher
        );
    }

    public void execute(
            Operation operation,
            String taskName,
            Runnable runnable
    ) {
        TrackingRunnable trackingRunnable =
                runnableFactory.wrap(operation, taskName, runnable);
        SubmissionGate gate = new SubmissionGate();
        Runnable command = gate.wrap(trackingRunnable.runnable());
        trackQueued(
                command,
                trackingRunnable.task(),
                false
        );

        try {
            delegate.execute(command);
        } catch (RuntimeException exception) {
            queuedTasks.remove(command);
            reject(trackingRunnable);
            throw exception;
        }
        markSubmitted(trackingRunnable.task());
        gate.open();
    }

    public Future<?> submit(String taskName, Runnable runnable) {
        Optional<Operation> currentOperation = OperationContext.current();
        if (currentOperation.isPresent()) {
            return submit(currentOperation.get(), taskName, runnable);
        }
        return submitImplicit(taskName, runnable);
    }

    public <T> Future<T> submit(String taskName, Callable<T> callable) {
        Optional<Operation> currentOperation = OperationContext.current();
        if (currentOperation.isPresent()) {
            return submit(currentOperation.get(), taskName, callable);
        }
        return submitImplicit(taskName, callable);
    }

    public void execute(String taskName, Runnable runnable) {
        execute(currentOperationOrThrow(), taskName, runnable);
    }

    public void runTask(String taskName, Runnable runnable) {
        Operation operation = currentOperationOrThrow();
        TrackingRunnable trackingRunnable =
                runnableFactory.wrap(operation, taskName, runnable);
        markSubmitted(trackingRunnable.task());
        trackingRunnable.runnable().run();
    }

    public <T> T callTask(String taskName, Callable<T> callable)
            throws Exception {
        Operation operation = currentOperationOrThrow();
        TrackingCallable<T> trackingCallable =
                callableFactory.wrap(operation, taskName, callable);
        markSubmitted(trackingCallable.task());
        return trackingCallable.callable().call();
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "Command must not be null");
        String taskName = resolveTaskName(command, "RunnableTask");
        Optional<Operation> operation = OperationContext.current();
        if (operation.isPresent()) {
            execute(operation.get(), taskName, command);
        } else {
            executeImplicit(taskName, command);
        }
    }

    @Override
    public Future<?> submit(Runnable task) {
        Objects.requireNonNull(task, "Task must not be null");
        return submit(resolveTaskName(task, "RunnableTask"), task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        Objects.requireNonNull(task, "Task must not be null");
        return submit(
                resolveTaskName(task, "RunnableTask"),
                Executors.callable(task, result)
        );
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "Task must not be null");
        return submit(resolveTaskName(task, "CallableTask"), task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks
    ) throws InterruptedException {
        try {
            return invokeAllInternal(tasks, 0, null);
        } catch (TimeoutException impossible) {
            throw new AssertionError(impossible);
        }
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks,
            long timeout,
            TimeUnit unit
    ) throws InterruptedException {
        Objects.requireNonNull(unit, "Time unit must not be null");
        try {
            return invokeAllInternal(tasks, timeout, unit);
        } catch (TimeoutException impossible) {
            throw new AssertionError(impossible);
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        try {
            return invokeAnyInternal(tasks, 0, null);
        } catch (TimeoutException impossible) {
            throw new AssertionError(impossible);
        }
    }

    @Override
    public <T> T invokeAny(
            Collection<? extends Callable<T>> tasks,
            long timeout,
            TimeUnit unit
    ) throws InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(unit, "Time unit must not be null");
        return invokeAnyInternal(tasks, timeout, unit);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> neverStarted = delegate.shutdownNow();
        neverStarted.forEach(this::cancelQueued);
        clearFinishedQueuedTasks();
        return neverStarted;
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    private <T> List<Future<T>> invokeAllInternal(
            Collection<? extends Callable<T>> sourceTasks,
            long timeout,
            TimeUnit unit
    ) throws InterruptedException, TimeoutException {
        List<Callable<T>> tasks = copyTasks(sourceTasks, false);
        OperationScope scope = operationScope("invokeAll");
        List<Future<T>> futures = new ArrayList<>(tasks.size());
        long deadline = unit == null
                ? 0
                : System.nanoTime() + unit.toNanos(timeout);
        boolean timedOut = false;

        try {
            AtomicInteger index = new AtomicInteger();
            for (Callable<T> task : tasks) {
                futures.add(submit(
                        scope.operation(),
                        "invokeAll-" + index.incrementAndGet(),
                        task
                ));
            }

            for (Future<T> future : futures) {
                if (future.isDone()) {
                    continue;
                }
                try {
                    if (unit == null) {
                        future.get();
                    } else {
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0) {
                            timedOut = true;
                            break;
                        }
                        future.get(remaining, TimeUnit.NANOSECONDS);
                    }
                } catch (CancellationException | ExecutionException ignored) {
                    // invokeAll returns every future regardless of task outcome.
                } catch (TimeoutException timeoutFailure) {
                    timedOut = true;
                    break;
                }
            }
        } catch (RuntimeException submissionFailure) {
            cancelAll(futures);
            failImplicitScope(scope, submissionFailure);
            throw submissionFailure;
        } catch (InterruptedException interruption) {
            cancelAll(futures);
            failImplicitScope(scope, interruption);
            throw interruption;
        } finally {
            if (unit != null) {
                timedOut |= futures.stream().anyMatch(future -> !future.isDone());
                if (timedOut) {
                    cancelAll(futures);
                }
            }
        }

        finishImplicitScope(scope, timedOut);
        return futures;
    }

    private <T> T invokeAnyInternal(
            Collection<? extends Callable<T>> sourceTasks,
            long timeout,
            TimeUnit unit
    ) throws InterruptedException, ExecutionException, TimeoutException {
        List<Callable<T>> tasks = copyTasks(sourceTasks, true);
        OperationScope scope = operationScope("invokeAny");
        ExecutorCompletionService<T> completion =
                new ExecutorCompletionService<>(delegate);
        List<SubmittedCallable<T>> submissions =
                new ArrayList<>(tasks.size());
        long deadline = unit == null
                ? 0
                : System.nanoTime() + unit.toNanos(timeout);
        ExecutionException lastFailure = null;

        try {
            AtomicInteger index = new AtomicInteger();
            for (Callable<T> task : tasks) {
                submissions.add(submitForCompletion(
                        scope.operation(),
                        "invokeAny-" + index.incrementAndGet(),
                        task,
                        completion
                ));
            }

            for (int remaining = tasks.size(); remaining > 0; remaining--) {
                Future<T> completed;
                if (unit == null) {
                    completed = completion.take();
                } else {
                    long nanos = deadline - System.nanoTime();
                    if (nanos <= 0) {
                        throw new TimeoutException();
                    }
                    completed = completion.poll(
                            nanos,
                            TimeUnit.NANOSECONDS
                    );
                    if (completed == null) {
                        throw new TimeoutException();
                    }
                }

                try {
                    T result = completed.get();
                    cancelAllSubmitted(submissions);
                    finishImplicitScope(scope, false);
                    return result;
                } catch (CancellationException cancelled) {
                    lastFailure = new ExecutionException(cancelled);
                } catch (ExecutionException failure) {
                    lastFailure = failure;
                }
            }
        } catch (TimeoutException timeoutFailure) {
            cancelAllSubmitted(submissions);
            timeoutImplicitScope(scope);
            throw timeoutFailure;
        } catch (InterruptedException interruption) {
            cancelAllSubmitted(submissions);
            failImplicitScope(scope, interruption);
            throw interruption;
        } catch (RuntimeException submissionFailure) {
            cancelAllSubmitted(submissions);
            failImplicitScope(scope, submissionFailure);
            throw submissionFailure;
        }

        cancelAllSubmitted(submissions);
        ExecutionException failure = lastFailure == null
                ? new ExecutionException(
                        new IllegalStateException("No task completed")
                )
                : lastFailure;
        failImplicitScope(scope, failure);
        throw failure;
    }

    private Future<?> submitImplicit(String taskName, Runnable runnable) {
        Operation operation = createImplicitOperation(taskName);
        TrackingRunnable trackingRunnable =
                runnableFactory.wrap(operation, taskName, runnable);
        TrackingRunnable implicit = new TrackingRunnable(
                trackingRunnable.task(),
                trackingRunnable.runnable(),
                true
        );
        SubmissionGate gate = new SubmissionGate();
        Future<?> delegateFuture;
        try {
            delegateFuture = delegate.submit(gate.wrap(
                    () -> runImplicitRunnable(implicit)
            ));
        } catch (RuntimeException exception) {
            reject(implicit);
            throw exception;
        }
        markSubmitted(implicit.task());
        gate.open();
        trackQueued(delegateFuture, implicit.task(), true);

        return new TrackingFuture<>(
                delegateFuture,
                implicit.task(),
                clock,
                eventPublisher,
                true
        );
    }

    private <T> Future<T> submitImplicit(
            String taskName,
            Callable<T> callable
    ) {
        Operation operation = createImplicitOperation(taskName);
        TrackingCallable<T> trackingCallable =
                callableFactory.wrap(operation, taskName, callable);
        TrackingCallable<T> implicit = new TrackingCallable<>(
                trackingCallable.task(),
                trackingCallable.callable(),
                true
        );
        SubmissionGate gate = new SubmissionGate();
        Future<T> delegateFuture;
        try {
            delegateFuture = delegate.submit(gate.wrap(
                    () -> callImplicitCallable(implicit)
            ));
        } catch (RuntimeException exception) {
            reject(implicit);
            throw exception;
        }
        markSubmitted(implicit.task());
        gate.open();
        trackQueued(delegateFuture, implicit.task(), true);

        return new TrackingFuture<>(
                delegateFuture,
                implicit.task(),
                clock,
                eventPublisher,
                true
        );
    }

    private void executeImplicit(String taskName, Runnable runnable) {
        Operation operation = createImplicitOperation(taskName);
        TrackingRunnable trackingRunnable =
                runnableFactory.wrap(operation, taskName, runnable);
        TrackingRunnable implicit = new TrackingRunnable(
                trackingRunnable.task(),
                trackingRunnable.runnable(),
                true
        );
        SubmissionGate gate = new SubmissionGate();
        Runnable command = gate.wrap(() -> runImplicitRunnable(implicit));
        trackQueued(command, implicit.task(), true);

        try {
            delegate.execute(command);
        } catch (RuntimeException exception) {
            queuedTasks.remove(command);
            reject(implicit);
            throw exception;
        }
        markSubmitted(implicit.task());
        gate.open();
    }

    private <T> SubmittedCallable<T> submitForCompletion(
            Operation operation,
            String taskName,
            Callable<T> callable,
            ExecutorCompletionService<T> completion
    ) {
        TrackingCallable<T> tracking =
                callableFactory.wrap(operation, taskName, callable);
        SubmissionGate gate = new SubmissionGate();
        Future<T> delegateFuture;
        try {
            delegateFuture = completion.submit(
                    gate.wrap(tracking.callable())
            );
        } catch (RuntimeException exception) {
            reject(tracking);
            throw exception;
        }
        markSubmitted(tracking.task());
        gate.open();
        trackQueued(delegateFuture, tracking.task(), false);

        TrackingFuture<T> future = new TrackingFuture<>(
                delegateFuture,
                tracking.task(),
                clock,
                eventPublisher
        );
        return new SubmittedCallable<>(delegateFuture, future);
    }

    private void markSubmitted(Task task) {
        task.submit();
        eventPublisher.publish(new GhostWorkEvent(
                GhostWorkEventType.TASK_SUBMITTED,
                OperationView.from(task.getParentOperation()),
                TaskView.from(task),
                null
        ));
    }

    private static Operation currentOperationOrThrow() {
        return OperationContext.current()
                .orElseThrow(() -> new IllegalStateException(
                        "OperationContext is empty"
                ));
    }

    private OperationScope operationScope(String name) {
        Optional<Operation> current = OperationContext.current();
        if (current.isPresent()) {
            return new OperationScope(current.get(), false);
        }
        return new OperationScope(createImplicitOperation(name), true);
    }

    private Operation createImplicitOperation(String taskName) {
        Operation operation = new Operation("Implicit:" + taskName);
        registry.registerOperation(operation);
        return operation;
    }

    private void runImplicitRunnable(TrackingRunnable trackingRunnable) {
        try {
            trackingRunnable.runnable().run();
            completeImplicitOperationIfPossible(
                    trackingRunnable.task().getParentOperation()
            );
        } catch (RuntimeException | Error original) {
            failImplicitOperationIfPossible(
                    trackingRunnable.task().getParentOperation(),
                    original
            );
            throw original;
        }
    }

    private <T> T callImplicitCallable(TrackingCallable<T> trackingCallable)
            throws Exception {
        try {
            T result = trackingCallable.callable().call();
            completeImplicitOperationIfPossible(
                    trackingCallable.task().getParentOperation()
            );
            return result;
        } catch (Exception original) {
            failImplicitOperationIfPossible(
                    trackingCallable.task().getParentOperation(),
                    original
            );
            throw original;
        } catch (Error original) {
            failImplicitOperationIfPossible(
                    trackingCallable.task().getParentOperation(),
                    original
            );
            throw original;
        }
    }

    private void finishImplicitScope(
            OperationScope scope,
            boolean timedOut
    ) {
        if (!scope.implicit()) {
            return;
        }
        if (timedOut) {
            timeoutImplicitScope(scope);
            return;
        }

        List<Task> tasks =
                registry.findTasksByOperation(scope.operation().getId());
        boolean failed = tasks.stream().anyMatch(task ->
                task.getState() == TaskState.FAILED
                        || task.getState() == TaskState.REJECTED
        );
        if (failed) {
            failImplicitOperationIfPossible(
                    scope.operation(),
                    new IllegalStateException("A bulk task failed")
            );
        } else {
            completeImplicitOperationIfPossible(scope.operation());
        }
    }

    private void failImplicitScope(
            OperationScope scope,
            Throwable failure
    ) {
        if (scope.implicit()) {
            failImplicitOperationIfPossible(scope.operation(), failure);
        }
    }

    private void timeoutImplicitScope(OperationScope scope) {
        if (!scope.implicit()) {
            return;
        }
        try {
            scope.operation().timeout();
            eventPublisher.publish(new GhostWorkEvent(
                    GhostWorkEventType.OPERATION_TIMED_OUT,
                    OperationView.from(scope.operation()),
                    null,
                    null
            ));
        } catch (IllegalStateException ignored) {
            // The operation was finalized concurrently.
        }
    }

    private void completeImplicitOperationIfPossible(Operation operation) {
        try {
            operation.complete();
            eventPublisher.publish(new GhostWorkEvent(
                    GhostWorkEventType.OPERATION_COMPLETED,
                    OperationView.from(operation),
                    null,
                    null
            ));
        } catch (IllegalStateException ignored) {
            // The operation was finalized concurrently.
        }
    }

    private void failImplicitOperationIfPossible(
            Operation operation,
            Throwable failure
    ) {
        try {
            operation.fail();
            eventPublisher.publish(new GhostWorkEvent(
                    GhostWorkEventType.OPERATION_FAILED,
                    OperationView.from(operation),
                    null,
                    failure
            ));
        } catch (IllegalStateException stateFailure) {
            failure.addSuppressed(stateFailure);
        }
    }

    private void reject(TrackingRunnable trackingRunnable) {
        rejectTask(
                trackingRunnable.task(),
                trackingRunnable.implicitOperation()
        );
    }

    private void reject(TrackingCallable<?> trackingCallable) {
        rejectTask(
                trackingCallable.task(),
                trackingCallable.implicitOperation()
        );
    }

    private void rejectTask(Task task, boolean implicitOperation) {
        task.reject(Instant.now(clock));
        eventPublisher.publish(new GhostWorkEvent(
                GhostWorkEventType.TASK_REJECTED,
                OperationView.from(task.getParentOperation()),
                TaskView.from(task),
                null
        ));

        if (implicitOperation) {
            failImplicitOperationIfPossible(
                    task.getParentOperation(),
                    new IllegalStateException("Implicit task was rejected")
            );
        }
    }

    private void trackQueued(
            Object queued,
            Task task,
            boolean implicitOperation
    ) {
        clearFinishedQueuedTasks();
        if (queued instanceof Runnable runnable) {
            queuedTasks.put(
                    runnable,
                    new QueuedTask(task, implicitOperation)
            );
        }
    }

    private void cancelQueued(Runnable queued) {
        if (queued instanceof Future<?> future) {
            future.cancel(false);
        }
        QueuedTask tracked = queuedTasks.remove(queued);
        if (tracked == null) {
            return;
        }
        cancelTaskIfPossible(tracked.task(), tracked.implicitOperation());
    }

    private void cancelTaskIfPossible(Task task, boolean implicitOperation) {
        try {
            task.cancel(Instant.now(clock));
            if (implicitOperation) {
                completeImplicitOperationIfPossible(
                        task.getParentOperation()
                );
            }
            eventPublisher.publish(new GhostWorkEvent(
                    GhostWorkEventType.TASK_CANCELLED,
                    OperationView.from(task.getParentOperation()),
                    TaskView.from(task),
                    null
            ));
        } catch (IllegalStateException ignored) {
            // Completion won the race.
        }
    }

    private void clearFinishedQueuedTasks() {
        queuedTasks.entrySet().removeIf(entry ->
                entry.getValue().task().isFinished()
        );
    }

    private static <T> List<Callable<T>> copyTasks(
            Collection<? extends Callable<T>> tasks,
            boolean requireNonEmpty
    ) {
        Objects.requireNonNull(tasks, "Tasks must not be null");
        if (requireNonEmpty && tasks.isEmpty()) {
            throw new IllegalArgumentException("Tasks must not be empty");
        }
        List<Callable<T>> copy = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            copy.add(Objects.requireNonNull(task, "Task must not be null"));
        }
        return copy;
    }

    private static String resolveTaskName(
            Object task,
            String fallback
    ) {
        String simpleName = task.getClass().getSimpleName();
        return simpleName.isBlank() ? fallback : simpleName;
    }

    private static void cancelAll(List<? extends Future<?>> futures) {
        futures.forEach(future -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
    }

    private static void cancelAllSubmitted(
            List<? extends SubmittedCallable<?>> submissions
    ) {
        submissions.forEach(submission -> {
            if (!submission.trackingFuture().isDone()) {
                submission.trackingFuture().cancel(true);
            }
        });
    }

    private record OperationScope(
            Operation operation,
            boolean implicit
    ) {
    }

    private record QueuedTask(
            Task task,
            boolean implicitOperation
    ) {
    }

    private record SubmittedCallable<T>(
            Future<T> delegateFuture,
            TrackingFuture<T> trackingFuture
    ) {
    }

    private static final class SubmissionGate {
        private final CountDownLatch accepted = new CountDownLatch(1);

        Runnable wrap(Runnable runnable) {
            return () -> {
                awaitAcceptance();
                runnable.run();
            };
        }

        <T> Callable<T> wrap(Callable<T> callable) {
            return () -> {
                awaitAcceptance();
                return callable.call();
            };
        }

        void open() {
            accepted.countDown();
        }

        private void awaitAcceptance() {
            boolean interrupted = false;
            while (true) {
                try {
                    accepted.await();
                    break;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
