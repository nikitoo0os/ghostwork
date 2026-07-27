package io.nikitoo0os;

import java.util.Objects;

public record TaskExecutionMetadata(
        ExecutorMetadata executor,
        ThreadMetadata thread,
        TaskSourceMetadata source
) {
    public TaskExecutionMetadata {
        executor = Objects.requireNonNull(
                executor,
                "Executor metadata must not be null"
        );
        source = Objects.requireNonNull(
                source,
                "Task source metadata must not be null"
        );
    }

    public TaskExecutionMetadata(
            ExecutorMetadata executor,
            ThreadMetadata thread
    ) {
        this(executor, thread, TaskSourceMetadata.capture());
    }

    public TaskExecutionMetadata startedOnCurrentThread() {
        return new TaskExecutionMetadata(
                executor,
                ThreadMetadata.current(),
                source
        );
    }
}
