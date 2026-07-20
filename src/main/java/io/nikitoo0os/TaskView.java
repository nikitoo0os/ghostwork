package io.nikitoo0os;

import io.nikitoo0os.entity.Task;
import io.nikitoo0os.entity.enums.TaskState;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TaskView(
        UUID id,
        String name,
        UUID operationId,
        TaskState state,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
    public static TaskView from(Task task) {
        Objects.requireNonNull(task, "Task must not be null");

        return new TaskView(
                task.getId(),
                task.getName(),
                task.getParentOperation().getId(),
                task.getState(),
                task.getCreatedAt(),
                task.getStartedAt(),
                task.getFinishedAt()
        );
    }
}