package io.nikitoo0os;

import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Task;

import java.time.Duration;
import java.util.Objects;

public final class GhostTaskInfo {
    private final Operation operation;
    private final Task task;
    private final Duration ghostDuration;

    public GhostTaskInfo(
            Operation operation,
            Task task,
            Duration ghostDuration
    ) {
        this.operation = Objects.requireNonNull(operation);
        this.task = Objects.requireNonNull(task);
        this.ghostDuration = Objects.requireNonNull(ghostDuration);
    }

    @Override
    public String toString() {
        return "Operation id: " + operation.getId() + "\n" +
                "Operation name: " + operation.getName() + "\n" +
                "Operation state: " + operation.getState() + "\n" +
                "Task id: " + task.getId() + "\n" +
                "Task name: " + task.getName() + "\n" +
                "Task state: " + task.getState() + "\n" +
                "Ghost duration: " + ghostDuration.toMillis() + " ms";
    }
}
