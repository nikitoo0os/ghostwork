package io.nikitoo0os.factory;

import io.nikitoo0os.entity.Task;
import io.nikitoo0os.wrap.WrappedRunnable;

import java.time.Instant;

public record TrackingRunnable(
        Task task,
        WrappedRunnable runnable,
        boolean implicitOperation
) {

    public TrackingRunnable(
            Task task,
            WrappedRunnable runnable
    ) {
        this(task, runnable, false);
    }

    public void reject(Instant rejectedAt) {
        task.reject(rejectedAt);
    }
}