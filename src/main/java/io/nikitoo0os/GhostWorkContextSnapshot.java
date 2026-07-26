package io.nikitoo0os;

import java.util.Objects;
import java.util.UUID;

public record GhostWorkContextSnapshot(
        UUID operationId,
        UUID taskId,
        UUID scheduleId,
        Long scheduleExecutionNumber,
        CorrelationId correlationId,
        CancellationToken cancellationToken,
        boolean detached
) {
    public GhostWorkContextSnapshot {
        Objects.requireNonNull(
                correlationId,
                "Correlation id must not be null"
        );
        cancellationToken = cancellationToken == null
                ? CancellationToken.none()
                : cancellationToken;
        if (scheduleExecutionNumber != null
                && scheduleExecutionNumber < 1) {
            throw new IllegalArgumentException(
                    "Schedule execution number must be positive"
            );
        }
    }

    public static GhostWorkContextSnapshot operation(
            UUID operationId,
            CorrelationId correlationId
    ) {
        return new GhostWorkContextSnapshot(
                Objects.requireNonNull(operationId),
                null,
                null,
                null,
                correlationId,
                CancellationToken.none(),
                false
        );
    }

    public GhostWorkContextSnapshot withTask(
            UUID taskId,
            CancellationToken token,
            boolean detached
    ) {
        return new GhostWorkContextSnapshot(
                operationId,
                Objects.requireNonNull(taskId),
                scheduleId,
                scheduleExecutionNumber,
                correlationId,
                token,
                detached
        );
    }

    public GhostWorkContextSnapshot withSchedule(
            UUID scheduleId,
            long executionNumber
    ) {
        return new GhostWorkContextSnapshot(
                operationId,
                taskId,
                Objects.requireNonNull(scheduleId),
                executionNumber,
                correlationId,
                cancellationToken,
                detached
        );
    }
}
