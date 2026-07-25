package io.nikitoo0os;

import java.util.Objects;

public record TaskExecutionMetadata(
        ExecutorMetadata executor,
        ThreadMetadata thread
) {
    public TaskExecutionMetadata {
        executor = Objects.requireNonNull(
                executor,
                "Executor metadata must not be null"
        );
    }

    public TaskExecutionMetadata startedOnCurrentThread() {
        return new TaskExecutionMetadata(executor, ThreadMetadata.current());
    }
}
