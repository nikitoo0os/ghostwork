package io.nikitoo0os.factory;

import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.wrap.WrappedCallable;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Callable;

public final class TrackingCallableFactory {
    private final Registry registry;
    private final Clock clock;
    private final GhostWorkEventPublisher eventPublisher;

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
    }

    public <T> TrackingCallable<T> wrap(
            Operation operation,
            String taskName,
            Callable<T> delegate
    ) {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(delegate);

        Task task = new Task(taskName, operation);
        registry.registerTask(task);

        return new TrackingCallable<>(
                task,
                new WrappedCallable<>(delegate, task, clock, eventPublisher)
        );
    }
}