package io.nikitoo0os;

import java.time.Instant;

public record OperationCancellationView(
        boolean requested,
        Instant requestedAt,
        CancellationCause cause,
        int targetedTasks,
        int cancelledTasks,
        int stillActiveTasks
) {
    public OperationCancellationView {
        if (requested && (requestedAt == null || cause == null)) {
            throw new IllegalArgumentException(
                    "Requested cancellation requires a cause and timestamp"
            );
        }
        if (targetedTasks < 0
                || cancelledTasks < 0
                || stillActiveTasks < 0) {
            throw new IllegalArgumentException(
                    "Cancellation task counts must not be negative"
            );
        }
    }

    public static OperationCancellationView none() {
        return new OperationCancellationView(
                false, null, null, 0, 0, 0
        );
    }
}
