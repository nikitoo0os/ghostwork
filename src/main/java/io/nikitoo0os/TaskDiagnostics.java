package io.nikitoo0os;

import io.nikitoo0os.entity.Task;
import io.nikitoo0os.entity.enums.TaskState;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TaskDiagnostics(
        UUID taskId,
        UUID operationId,
        String taskName,
        TaskState state,
        Instant submittedAt,
        Instant startedAt,
        Instant finishedAt,
        Duration queueDuration,
        Duration executionDuration,
        TaskExecutionMetadata executionMetadata
) {
    public static TaskDiagnostics from(Task task, Instant observedAt) {
        Objects.requireNonNull(task, "Task must not be null");
        Objects.requireNonNull(observedAt, "Observed time must not be null");

        Instant submittedAt = task.getSubmittedAt();
        Instant startedAt = task.getStartedAt();
        Instant finishedAt = task.getFinishedAt();
        Duration queueDuration = submittedAt == null
                ? Duration.ZERO
                : Duration.between(
                        submittedAt,
                        startedAt == null ? observedAt : startedAt
                );
        Duration executionDuration = startedAt == null
                ? Duration.ZERO
                : Duration.between(
                        startedAt,
                        finishedAt == null ? observedAt : finishedAt
                );

        return new TaskDiagnostics(
                task.getId(),
                task.getParentOperation().getId(),
                task.getName(),
                task.getState(),
                submittedAt,
                startedAt,
                finishedAt,
                queueDuration,
                executionDuration,
                task.getExecutionMetadata()
        );
    }
}
