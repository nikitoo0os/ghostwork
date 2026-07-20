package io.nikitoo0os;

import io.nikitoo0os.context.OperationContext;
import io.nikitoo0os.entity.Operation;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class TrackingExecutorService {
    private final ExecutorService delegate;
    private final TrackingRunnableFactory runnableFactory;
    private final TrackingCallableFactory callableFactory;

    private final GhostWorkEventPublisher eventPublisher;

    private final Clock clock;

    public TrackingExecutorService(ExecutorService delegate, TrackingRunnableFactory runnableFactory, TrackingCallableFactory callableFactory, Clock clock, GhostWorkEventPublisher eventPublisher) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate executor must not be null");
        this.runnableFactory = Objects.requireNonNull(runnableFactory, "Runnable factory must not be null");
        this.callableFactory = Objects.requireNonNull(callableFactory, "Callable factory must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher,
                "Event publisher must not be null"
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
        this(delegate, runnableFactory, callableFactory, Clock.systemUTC());
    }

    public Future<?> submit(
            Operation operation,
            String taskName,
            Runnable runnable
    ) {
        TrackingRunnable trackingRunnable =
                runnableFactory.wrap(operation, taskName, runnable);

        return submitOrReject(
                trackingRunnable,
                () -> delegate.submit(trackingRunnable.runnable())
        );
    }

    public <T> Future<T> submit(
            Operation operation,
            String taskName,
            Callable<T> callable
    ) {
        TrackingCallable<T> trackingCallable =
                callableFactory.wrap(operation, taskName, callable);

        return submitOrReject(
                trackingCallable,
                () -> delegate.submit(trackingCallable.callable())
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
        Operation operation = currentOperationOrThrow();

        return submit(operation, taskName, runnable);
    }

    public <T> Future<T> submit(
            String taskName,
            Callable<T> callable
    ) {
        Operation operation = currentOperationOrThrow();

        return submit(operation, taskName, callable);
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

    private static Operation currentOperationOrThrow() {
        return OperationContext.current()
                .orElseThrow(() -> new IllegalStateException(
                        "OperationContext is empty"
                ));
    }

    private <T> T submitOrReject(
            TrackingRunnable trackingRunnable,
            Supplier<T> submitAction
    ) {
        try {
            return submitAction.get();
        } catch (RuntimeException exception) {
            trackingRunnable.task().reject(Instant.now(clock));
            eventPublisher.publish(
                    new GhostWorkEvent(
                            GhostWorkEventType.TASK_REJECTED,
                            OperationView.from(trackingRunnable.task().getParentOperation()),
                            TaskView.from(trackingRunnable.task()),
                            null
                    )
            );
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
            trackingCallable.task().reject(Instant.now(clock));
            eventPublisher.publish(
                    new GhostWorkEvent(
                            GhostWorkEventType.TASK_REJECTED,
                            OperationView.from(trackingCallable.task().getParentOperation()),
                            TaskView.from(trackingCallable.task()),
                            null
                    )
            );
            throw exception;
        }
    }
}
