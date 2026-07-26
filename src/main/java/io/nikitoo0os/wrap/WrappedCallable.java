package io.nikitoo0os.wrap;

import io.nikitoo0os.OperationView;
import io.nikitoo0os.TaskView;
import io.nikitoo0os.context.OperationContext;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.event.GhostWorkEvent;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.event.GhostWorkEventType;
import io.nikitoo0os.GhostWorkContext;
import io.nikitoo0os.entity.CancellationCoordinator;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

public final class WrappedCallable<T> implements Callable<T> {

    private final Callable<T> delegate;
    private final Task task;
    private final Clock clock;

    private final GhostWorkEventPublisher eventPublisher;
    private final CancellationCoordinator cancellation;

    public WrappedCallable(Callable<T> delegate, Task task) {
        this(delegate, task, Clock.systemUTC(), new GhostWorkEventPublisher());
    }

    public WrappedCallable(Callable<T> delegate, Task task, Clock clock, GhostWorkEventPublisher eventPublisher) {
        this(
                delegate,
                task,
                clock,
                eventPublisher,
                standaloneCoordinator(task, clock, eventPublisher)
        );
    }

    public WrappedCallable(
            Callable<T> delegate,
            Task task,
            Clock clock,
            GhostWorkEventPublisher eventPublisher,
            CancellationCoordinator cancellation
    ) {
        this.delegate = Objects.requireNonNull(delegate);
        this.task = Objects.requireNonNull(task);
        this.clock = Objects.requireNonNull(clock);
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher,
                "Event publisher must not be null"
        );
        this.cancellation = Objects.requireNonNull(cancellation);
    }

    @Override
    public T call() throws Exception {
        task.start(Instant.now(clock));
        eventPublisher.publish(new GhostWorkEvent(
                GhostWorkEventType.TASK_STARTED,
                OperationView.from(task.getParentOperation()),
                TaskView.from(task),
                null
        ));
        try (OperationContext.Scope ignored =
                     OperationContext.open(task.getParentOperation());
             GhostWorkContext.Scope taskScope = GhostWorkContext.openTask(
                     task.getId(),
                     cancellation.token(task.getId()),
                     task.getSubmissionContext(),
                     cancellation.view(task.getId()).mode()
                             == io.nikitoo0os.TaskCancellationMode.DETACHED
             )) {
            T result = delegate.call();
            task.complete(Instant.now(clock));
            cancellation.terminal(task.getId());
            eventPublisher.publish(new GhostWorkEvent(
                    GhostWorkEventType.TASK_COMPLETED,
                    OperationView.from(task.getParentOperation()),
                    TaskView.from(task),
                    null
            ));
            return result;
        } catch (Throwable original) {
            try {
                if (original instanceof CancellationException
                        && cancellation.view(task.getId())
                        .cancellationRequested()) {
                    task.cancel(Instant.now(clock));
                    cancellation.cancellationCompleted(task.getId());
                    eventPublisher.publish(new GhostWorkEvent(
                            GhostWorkEventType.TASK_CANCELLED,
                            OperationView.from(task.getParentOperation()),
                            TaskView.from(task),
                            original
                    ));
                } else if (original instanceof InterruptedException
                        && cancellation.view(task.getId())
                        .interruptRequested()) {
                    cancellation.markInterruptObserved(task.getId());
                    task.cancel(Instant.now(clock));
                    cancellation.cancellationCompleted(task.getId());
                    eventPublisher.publish(new GhostWorkEvent(
                            GhostWorkEventType.TASK_CANCELLED,
                            OperationView.from(task.getParentOperation()),
                            TaskView.from(task),
                            original
                    ));
                    Thread.currentThread().interrupt();
                } else {
                    task.fail(Instant.now(clock));
                    cancellation.terminal(task.getId());
                    eventPublisher.publish(new GhostWorkEvent(
                            GhostWorkEventType.TASK_FAILED,
                            OperationView.from(task.getParentOperation()),
                            TaskView.from(task),
                            original
                    ));
                }
            } catch (Throwable stateFailure) {
                original.addSuppressed(stateFailure);
            }

            throw original;
        }
    }

    private static CancellationCoordinator standaloneCoordinator(
            Task task,
            Clock clock,
            GhostWorkEventPublisher events
    ) {
        CancellationCoordinator coordinator =
                new CancellationCoordinator(clock, events);
        coordinator.register(
                task,
                io.nikitoo0os.TaskCancellationMode.INHERIT,
                null
        );
        coordinator.markFutureUnavailable(task.getId());
        return coordinator;
    }
}
