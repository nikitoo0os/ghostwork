package io.nikitoo0os.wrap;

import io.nikitoo0os.OperationView;
import io.nikitoo0os.TaskView;
import io.nikitoo0os.context.OperationContext;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.event.GhostWorkEvent;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.event.GhostWorkEventType;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;

public final class WrappedCallable<T> implements Callable<T> {

    private final Callable<T> delegate;
    private final Task task;
    private final Clock clock;

    private final GhostWorkEventPublisher eventPublisher;

    public WrappedCallable(Callable<T> delegate, Task task) {
        this(delegate, task, Clock.systemUTC(), new GhostWorkEventPublisher());
    }

    public WrappedCallable(Callable<T> delegate, Task task, Clock clock, GhostWorkEventPublisher eventPublisher) {
        this.delegate = Objects.requireNonNull(delegate);
        this.task = Objects.requireNonNull(task);
        this.clock = Objects.requireNonNull(clock);
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher,
                "Event publisher must not be null"
        );
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
        try (OperationContext.Scope ignored = OperationContext.open(task.getParentOperation())) {
            T result = delegate.call();
            task.complete(Instant.now(clock));
            eventPublisher.publish(new GhostWorkEvent(
                    GhostWorkEventType.TASK_COMPLETED,
                    OperationView.from(task.getParentOperation()),
                    TaskView.from(task),
                    null
            ));
            return result;
        } catch (Throwable original) {
            try {
                task.fail(Instant.now(clock));
                eventPublisher.publish(new GhostWorkEvent(
                        GhostWorkEventType.TASK_FAILED,
                        OperationView.from(task.getParentOperation()),
                        TaskView.from(task),
                        original
                ));
            } catch (Throwable stateFailure) {
                original.addSuppressed(stateFailure);
            }

            throw original;
        }
    }
}
