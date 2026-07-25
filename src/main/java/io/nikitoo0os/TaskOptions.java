package io.nikitoo0os;

import java.util.Objects;

public record TaskOptions(
        String name,
        TaskCancellationMode cancellationMode
) {
    public TaskOptions {
        Objects.requireNonNull(name, "Task name must not be null");
        Objects.requireNonNull(cancellationMode, "Cancellation mode must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Task name must not be blank");
        }
    }

    public static TaskOptions inherited(String name) {
        return new TaskOptions(name, TaskCancellationMode.INHERIT);
    }

    public static TaskOptions detached(String name) {
        return new TaskOptions(name, TaskCancellationMode.DETACHED);
    }
}
