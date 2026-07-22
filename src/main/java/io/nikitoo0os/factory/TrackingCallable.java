package io.nikitoo0os.factory;

import io.nikitoo0os.entity.Task;
import io.nikitoo0os.wrap.WrappedCallable;

import java.time.Instant;

public record TrackingCallable<T>(
        Task task,
        WrappedCallable<T> callable,
        boolean implicitOperation
) {

    public TrackingCallable(
            Task task,
            WrappedCallable<T> callable
    ) {
        this(task, callable, false);
    }

    public void reject(Instant rejectedAt) {
        task.reject(rejectedAt);
    }
}