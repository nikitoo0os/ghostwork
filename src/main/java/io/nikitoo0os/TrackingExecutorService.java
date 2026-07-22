package io.nikitoo0os;

import io.nikitoo0os.context.OperationContext;
import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.event.GhostWorkEvent;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.event.GhostWorkEventType;
import io.nikitoo0os.factory.TrackingCallable;
import io.nikitoo0os.factory.TrackingCallableFactory;
import io.nikitoo0os.factory.TrackingRunnable;
import io.nikitoo0os.factory.TrackingRunnableFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class TrackingExecutorService {
    private final ExecutorService delegate;
    private final Registry registry;
    private final TrackingRunnableFactory runnableFactory;
    private final TrackingCallableFactory callableFactory;
    private final Clock clock;
    private final GhostWorkEventPublisher eventPublisher;

    public TrackingExecutorService(
            ExecutorService delegate,
            TrackingRunnableFactory runnableFactory,
            TrackingCallableFactory callableFactory,
            Clock clock,
            GhostWorkEventPublisher eventPublisher,
            Registry registry
    ) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate executor must not be null");
        this.runnableFactory = Objects.requireNonNull(runnableFactory, "Runnable factory must not be null");
        this.callableFactory = Objects.requireNonNull(callableFactory, "Callable factory must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "Event publisher must not be null");
        this.registry = Objects.requireNonNull(registry, "Registry must not be null");
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
                new Registry()
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

        Future<?> delegateFuture = submitOrReject(
                trackingRunnable,
                () -> delegate.submit(trackingRunnable.runnable())
        );

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

        Future<T> delegateFuture = submitOrReject(
                trackingCallable,
                () -> delegate.submit(trackingCallable.callable())
        );

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

        submitOrReject(
                trackingRunnable,
                () -> {
                    delegate.execute(trackingRunnable.runnable());
                    return null;
                }
        );
    }

    public Future<?> submit(
            String taskName,
            Runnable runnable
    ) {
        Optional<Operation> currentOperation =
                OperationContext.current();

        if (currentOperation.isPresent()) {
            return submit(
                    currentOperation.get(),
                    taskName,
                    runnable
            );
        }

        return submitImplicit(taskName, runnable);
    }

    public <T> Future<T> submit(
            String taskName,
            Callable<T> callable
    ) {
        Optional<Operation> currentOperation =
                OperationContext.current();

        if (currentOperation.isPresent()) {
            return submit(
                    currentOperation.get(),
                    taskName,
                    callable
            );
        }

        return submitImplicit(taskName, callable);
    }

    public void execute(
            String taskName,
            Runnable runnable
    ) {
        Operation operation = currentOperationOrThrow();

        execute(operation, taskName, runnable);
    }

    public void shutdown() {
        delegate.shutdown();
    }

    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    private Future<?> submitImplicit(
            String taskName,
            Runnable runnable
    ) {
        Operation operation = createImplicitOperation(taskName);

        TrackingRunnable trackingRunnable =
                runnableFactory.wrap(operation, taskName, runnable);

        TrackingRunnable implicitTrackingRunnable =
                new TrackingRunnable(
                        trackingRunnable.task(),
                        trackingRunnable.runnable(),
                        true
                );

        Future<?> delegateFuture = submitOrReject(
                implicitTrackingRunnable,
                () -> delegate.submit(() -> runImplicitRunnable(
                        implicitTrackingRunnable
                ))
        );

        return new TrackingFuture<>(
                delegateFuture,
                implicitTrackingRunnable.task(),
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

        TrackingCallable<T> implicitTrackingCallable =
                new TrackingCallable<>(
                        trackingCallable.task(),
                        trackingCallable.callable(),
                        true
                );

        Future<T> delegateFuture = submitOrReject(
                implicitTrackingCallable,
                () -> delegate.submit(() -> callImplicitCallable(
                        implicitTrackingCallable
                ))
        );

        return new TrackingFuture<>(
                delegateFuture,
                implicitTrackingCallable.task(),
                clock,
                eventPublisher,
                true
        );
    }

    private static Operation currentOperationOrThrow() {
        return OperationContext.current()
                .orElseThrow(() -> new IllegalStateException(
                        "OperationContext is empty"
                ));
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
        } catch (RuntimeException original) {
            failImplicitOperationIfPossible(
                    trackingRunnable.task().getParentOperation(),
                    original
            );

            throw original;
        } catch (Error original) {
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

    private void completeImplicitOperationIfPossible(Operation operation) {
        try {
            operation.complete();

            eventPublisher.publish(
                    new GhostWorkEvent(
                            GhostWorkEventType.OPERATION_COMPLETED,
                            OperationView.from(operation),
                            null,
                            null
                    )
            );
        } catch (IllegalStateException ignored) {
            // The operation was already finalized concurrently.
        }
    }

    private void failImplicitOperationIfPossible(
            Operation operation,
            Throwable failure
    ) {
        try {
            operation.fail();

            eventPublisher.publish(
                    new GhostWorkEvent(
                            GhostWorkEventType.OPERATION_FAILED,
                            OperationView.from(operation),
                            null,
                            failure
                    )
            );
        } catch (IllegalStateException stateFailure) {
            failure.addSuppressed(stateFailure);
        }
    }

    private <T> T submitOrReject(
            TrackingRunnable trackingRunnable,
            Supplier<T> submitAction
    ) {
        try {
            return submitAction.get();
        } catch (RuntimeException exception) {
            reject(trackingRunnable);
            throw exception;
        }
    }

    private <T> T submitOrReject(
            TrackingCallable<?> trackingCallable,
            Supplier<T> submitAction
    ) {
        try {
            return submitAction.get();
        } catch (RuntimeException exception) {
            reject(trackingCallable);
            throw exception;
        }
    }

    private void reject(TrackingRunnable trackingRunnable) {
        trackingRunnable.task().reject(Instant.now(clock));

        eventPublisher.publish(
                new GhostWorkEvent(
                        GhostWorkEventType.TASK_REJECTED,
                        OperationView.from(trackingRunnable.task().getParentOperation()),
                        TaskView.from(trackingRunnable.task()),
                        null
                )
        );

        if (trackingRunnable.implicitOperation()) {
            failImplicitOperationIfPossible(
                    trackingRunnable.task().getParentOperation(),
                    new IllegalStateException("Implicit task was rejected")
            );
        }
    }

    private void reject(TrackingCallable<?> trackingCallable) {
        trackingCallable.task().reject(Instant.now(clock));

        eventPublisher.publish(
                new GhostWorkEvent(
                        GhostWorkEventType.TASK_REJECTED,
                        OperationView.from(trackingCallable.task().getParentOperation()),
                        TaskView.from(trackingCallable.task()),
                        null
                )
        );

        if (trackingCallable.implicitOperation()) {
            failImplicitOperationIfPossible(
                    trackingCallable.task().getParentOperation(),
                    new IllegalStateException("Implicit task was rejected")
            );
        }
    }
}