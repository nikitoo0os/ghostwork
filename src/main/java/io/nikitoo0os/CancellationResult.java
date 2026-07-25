package io.nikitoo0os;

import java.time.Instant;
import java.util.UUID;
import io.nikitoo0os.entity.enums.OperationState;

public record CancellationResult(
        UUID targetId,
        boolean found,
        boolean requestAccepted,
        int activeTasks,
        int queuedTasksTargeted,
        int runningTasksTargeted,
        int futureCancellationAttempts,
        int futureCancellationsAccepted,
        int stillActive,
        Instant requestedAt,
        CancellationCause cause,
        OperationState operationStateBefore
) {
    public CancellationResult(
            UUID targetId,
            boolean found,
            boolean requestAccepted,
            int activeTasks,
            int queuedTasksTargeted,
            int runningTasksTargeted,
            int futureCancellationAttempts,
            int futureCancellationsAccepted,
            int stillActive,
            Instant requestedAt,
            CancellationCause cause
    ) {
        this(
                targetId, found, requestAccepted, activeTasks,
                queuedTasksTargeted, runningTasksTargeted,
                futureCancellationAttempts, futureCancellationsAccepted,
                stillActive, requestedAt, cause, null
        );
    }

    public static CancellationResult notFound(
            UUID targetId,
            CancellationCause cause,
            Instant requestedAt
    ) {
        return new CancellationResult(
                targetId, false, false, 0, 0, 0, 0, 0, 0,
                requestedAt, cause, null
        );
    }
}
