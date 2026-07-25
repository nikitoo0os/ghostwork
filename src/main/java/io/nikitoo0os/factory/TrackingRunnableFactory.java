package io.nikitoo0os.factory;

import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.ExecutorMetadata;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.wrap.WrappedRunnable;

import java.time.Clock;
import java.util.Objects;

public final class TrackingRunnableFactory {
    private final Registry registry;
    private final Clock clock;
    private final GhostWorkEventPublisher eventPublisher;

    public TrackingRunnableFactory(Registry registry) {
        this(registry, Clock.systemUTC(), new GhostWorkEventPublisher());
    }

    public TrackingRunnableFactory(Registry registry, Clock clock) {
        this(registry, clock, new GhostWorkEventPublisher());
    }

    public TrackingRunnableFactory(
            Registry registry,
            Clock clock,
            GhostWorkEventPublisher eventPublisher
    ) {
        this.registry = Objects.requireNonNull(registry);
        this.clock = Objects.requireNonNull(clock);
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher,
                "Event publisher must not be null"
        );
    }

    public TrackingRunnable wrap(
            Operation operation,
            String taskName,
            Runnable delegate
    ) {
        return wrap(
                operation,
                taskName,
                delegate,
                new ExecutorMetadata(
                        null,
                        "unknown",
                        io.nikitoo0os.SubmissionSource.MANUAL_API
                )
        );
    }

    public TrackingRunnable wrap(
            Operation operation,
            String taskName,
            Runnable delegate,
            ExecutorMetadata executorMetadata
    ) {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(delegate);

        Task task = new Task(taskName, operation, executorMetadata);
        registry.registerTask(task);

        return new TrackingRunnable(
                task,
                new WrappedRunnable(delegate, task, clock, eventPublisher)
        );
    }

    public Registry registry() {
        return registry;
    }
}
