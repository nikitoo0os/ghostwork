package io.nikitoo0os.factory;

import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.ExecutorMetadata;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.event.GhostWorkEvent;
import io.nikitoo0os.event.GhostWorkEventType;
import io.nikitoo0os.OperationView;
import io.nikitoo0os.TaskView;
import io.nikitoo0os.wrap.WrappedCallable;
import io.nikitoo0os.TaskOptions;
import io.nikitoo0os.GhostWorkContext;
import io.nikitoo0os.entity.CancellationCoordinator;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Callable;

public final class TrackingCallableFactory {
    private final Registry registry;
    private final Clock clock;
    private final GhostWorkEventPublisher eventPublisher;
    private final CancellationCoordinator cancellation;

    public TrackingCallableFactory(Registry registry, Clock clock) {
        this(Objects.requireNonNull(registry), Objects.requireNonNull(clock), new GhostWorkEventPublisher());
    }

    public TrackingCallableFactory(Registry registry) {
        this(registry, Clock.systemUTC(), new GhostWorkEventPublisher());
    }

    public TrackingCallableFactory(Registry registry,
                                   Clock clock,
                                   GhostWorkEventPublisher eventPublisher) {
        this.registry = Objects.requireNonNull(registry);
        this.clock = Objects.requireNonNull(clock);
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher,
                "Event publisher must not be null"
        );
        this.cancellation = registry.cancellationCoordinator(
                clock,
                eventPublisher
        );
    }

    public <T> TrackingCallable<T> wrap(
            Operation operation,
            String taskName,
            Callable<T> delegate
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

    public <T> TrackingCallable<T> wrap(
            Operation operation,
            String taskName,
            Callable<T> delegate,
            ExecutorMetadata executorMetadata
    ) {
        return wrap(
                operation,
                TaskOptions.inherited(taskName),
                delegate,
                executorMetadata
        );
    }

    public <T> TrackingCallable<T> wrap(
            Operation operation,
            TaskOptions options,
            Callable<T> delegate,
            ExecutorMetadata executorMetadata
    ) {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(delegate);
        Objects.requireNonNull(options);

        Task task = new Task(options.name(), operation, executorMetadata);
        registry.registerTask(task);
        eventPublisher.publishTaskCreated(
                OperationView.from(operation),
                TaskView.from(task)
        );
        cancellation.register(
                task,
                options.cancellationMode(),
                GhostWorkContext.currentTaskId().orElse(null)
        );

        return new TrackingCallable<>(
                task,
                new WrappedCallable<>(
                        delegate,
                        task,
                        clock,
                        eventPublisher,
                        cancellation
                )
        );
    }

    public Registry registry() {
        return registry;
    }

    public CancellationCoordinator cancellationCoordinator() {
        return cancellation;
    }
}
