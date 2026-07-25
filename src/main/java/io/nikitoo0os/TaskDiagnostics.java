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
        TaskExecutionMetadata executionMetadata,
        UUID parentTaskId,
        TaskCancellationView cancellation,
        TaskClassification classification
) {
    public TaskDiagnostics {
        Objects.requireNonNull(taskId, "Task id must not be null");
        Objects.requireNonNull(operationId, "Operation id must not be null");
        Objects.requireNonNull(taskName, "Task name must not be null");
        Objects.requireNonNull(state, "Task state must not be null");
        Objects.requireNonNull(cancellation, "Cancellation must not be null");
        Objects.requireNonNull(classification, "Classification must not be null");
    }

    public TaskDiagnostics(
            UUID taskId,
            UUID operationId,
            String taskName,
            TaskState state,
            Instant submittedAt,
            Instant startedAt,
            Instant finishedAt,
            Duration queueDuration,
            Duration executionDuration,
            TaskExecutionMetadata executionMetadata,
            UUID parentTaskId,
            TaskCancellationView cancellation
    ) {
        this(
                taskId, operationId, taskName, state, submittedAt,
                startedAt, finishedAt, queueDuration, executionDuration,
                executionMetadata, parentTaskId, cancellation,
                classify(cancellation)
        );
    }

    public static TaskDiagnostics from(Task task, Instant observedAt) {
        return from(
                task,
                observedAt,
                null,
                TaskCancellationView.none(TaskCancellationMode.INHERIT)
        );
    }

    public static TaskDiagnostics from(
            Task task,
            Instant observedAt,
            UUID parentTaskId,
            TaskCancellationView cancellation
    ) {
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
                task.getExecutionMetadata(),
                parentTaskId,
                Objects.requireNonNull(cancellation),
                classify(task, cancellation)
        );
    }

    private static TaskClassification classify(
            Task task,
            TaskCancellationView cancellation
    ) {
        if (cancellation.mode() == TaskCancellationMode.DETACHED) {
            return TaskClassification.DETACHED;
        }
        if (cancellation.status() == CancellationStatus.IGNORED) {
            return TaskClassification.CANCELLATION_IGNORED;
        }
        if (!task.isFinished()
                && task.getParentOperation().getState()
                != io.nikitoo0os.entity.enums.OperationState.RUNNING) {
            return TaskClassification.GHOST;
        }
        return TaskClassification.NORMAL;
    }

    private static TaskClassification classify(
            TaskCancellationView cancellation
    ) {
        if (cancellation.mode() == TaskCancellationMode.DETACHED) {
            return TaskClassification.DETACHED;
        }
        if (cancellation.status() == CancellationStatus.IGNORED) {
            return TaskClassification.CANCELLATION_IGNORED;
        }
        return TaskClassification.NORMAL;
    }
}
