package io.nikitoo0os.factory;

import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.entity.WrappedRunnable;

import java.util.Objects;

public final class TrackingRunnableFactory {
    private final Registry registry;
    public TrackingRunnableFactory(Registry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    public WrappedRunnable wrap(Operation operation, String taskName, Runnable delegate){
        Task task = new Task(taskName, operation);
        registry.registerTask(task);
        return new WrappedRunnable(delegate, task);
    }
}
