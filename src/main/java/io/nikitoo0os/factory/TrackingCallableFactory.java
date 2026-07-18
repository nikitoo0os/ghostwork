package io.nikitoo0os.factory;

import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.wrap.WrappedCallable;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Callable;

public final class TrackingCallableFactory {
    private final Registry registry;
    private final Clock clock;

    public TrackingCallableFactory(Registry registry, Clock clock) {
        this.registry = Objects.requireNonNull(registry);
        this.clock = Objects.requireNonNull(clock);
    }
    public TrackingCallableFactory(Registry registry) {
        this(registry, Clock.systemUTC());
    }

    public <T> WrappedCallable<T> wrap(Operation operation, String taskName, Callable<T> delegate){
        Objects.requireNonNull(operation);
        Objects.requireNonNull(delegate);

        Task task = new Task(taskName, operation);
        registry.registerTask(task);
        return new WrappedCallable<>(delegate, task, clock);
    }
}
