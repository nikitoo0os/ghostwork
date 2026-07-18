package io.nikitoo0os.factory;

import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.wrap.WrappedRunnable;

import java.time.Clock;
import java.util.Objects;

public final class TrackingRunnableFactory {
    private final Registry registry;
    private final Clock clock;

    public TrackingRunnableFactory(Registry registry) {
        this(registry, Clock.systemUTC());
    }

    public TrackingRunnableFactory(Registry registry, Clock clock) {
        this.registry = Objects.requireNonNull(registry);
        this.clock = Objects.requireNonNull(clock);
    }

    public WrappedRunnable wrap(Operation operation, String taskName, Runnable delegate) {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(delegate);

        Task task = new Task(taskName, operation);
        registry.registerTask(task);
        return new WrappedRunnable(delegate, task, clock);
    }
}
