package io.nikitoo0os.factory;

import io.nikitoo0os.entity.Task;
import io.nikitoo0os.wrap.WrappedRunnable;

import java.time.Instant;

public record TrackingRunnable(
        Task task,
        WrappedRunnable runnable
) {

    public void reject(Instant rejectedAt) {
        task.reject(rejectedAt);
    }
}