package io.nikitoo0os.factory;

import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.wrap.WrappedCallable;

import java.util.Objects;
import java.util.concurrent.Callable;

public final class TrackingCallableFactory {
    private final Registry registry;

    public TrackingCallableFactory(Registry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    public <T> WrappedCallable<T> wrap(Operation operation, String taskName, Callable<T> delegate){
        Task task = new Task(taskName, operation);
        registry.registerTask(task);
        return new WrappedCallable<>(delegate, task);
    }
}
