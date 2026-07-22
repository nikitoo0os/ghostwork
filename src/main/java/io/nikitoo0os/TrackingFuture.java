package io.nikitoo0os;

import io.nikitoo0os.entity.Task;
import io.nikitoo0os.event.GhostWorkEvent;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.event.GhostWorkEventType;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class TrackingFuture<T> implements Future<T> {
    private final Future<T> delegate;
    private final Task task;
    private final Clock clock;
    private final GhostWorkEventPublisher eventPublisher;

    private final boolean implicitOperation;

    public TrackingFuture(
            Future<T> delegate,
            Task task,
            Clock clock,
            GhostWorkEventPublisher eventPublisher,
            boolean implicitOperation
    ) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate future must not be null");
        this.task = Objects.requireNonNull(task, "Task must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "Event publisher must not be null");
        this.implicitOperation = implicitOperation;
    }

    public TrackingFuture(
            Future<T> delegate,
            Task task,
            Clock clock,
            GhostWorkEventPublisher eventPublisher
    ) {
        this(delegate, task, clock, eventPublisher, false);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = delegate.cancel(mayInterruptIfRunning);

        if (cancelled) {
            cancelTaskIfPossible();
        }

        return cancelled;
    }

    private void cancelTaskIfPossible() {
        try {
            task.cancel(Instant.now(clock));

            if (implicitOperation) {
                completeImplicitOperationIfPossible();
            }

            eventPublisher.publish(
                    new GhostWorkEvent(
                            GhostWorkEventType.TASK_CANCELLED,
                            OperationView.from(task.getParentOperation()),
                            TaskView.from(task),
                            null
                    )
            );
        } catch (IllegalStateException ignored) {
            // The task was completed or failed concurrently with Future cancellation.
        }
    }

    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
        return delegate.isDone();
    }

    @Override
    public T get() throws java.util.concurrent.ExecutionException, InterruptedException {
        return delegate.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit)
            throws java.util.concurrent.ExecutionException,
            InterruptedException,
            java.util.concurrent.TimeoutException {
        return delegate.get(timeout, unit);
    }

    private void completeImplicitOperationIfPossible() {
        try {
            task.getParentOperation().complete();

            eventPublisher.publish(
                    new GhostWorkEvent(
                            GhostWorkEventType.OPERATION_COMPLETED,
                            OperationView.from(task.getParentOperation()),
                            null,
                            null
                    )
            );
        } catch (IllegalStateException ignored) {
            // The operation was already finalized concurrently.
        }
    }
}