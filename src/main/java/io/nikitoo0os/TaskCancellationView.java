package io.nikitoo0os;

import java.time.Instant;

public record TaskCancellationView(
        CancellationStatus status,
        boolean cancellationRequested,
        Instant cancellationRequestedAt,
        CancellationCause cancellationCause,
        boolean futureCancellationAvailable,
        boolean futureCancellationAttempted,
        boolean futureCancellationAccepted,
        boolean interruptRequested,
        boolean interruptObserved,
        boolean cooperativeCancellationObserved,
        Instant cancelledAt,
        boolean gracePeriodExceeded,
        int cancellationCallbackCount,
        String cancellationError,
        TaskCancellationMode mode,
        ChildCancellationPolicy policy
) {
    public TaskCancellationView {
        java.util.Objects.requireNonNull(status, "Status must not be null");
        java.util.Objects.requireNonNull(mode, "Mode must not be null");
        java.util.Objects.requireNonNull(policy, "Policy must not be null");
        if (cancellationRequested
                && (cancellationRequestedAt == null
                || cancellationCause == null)) {
            throw new IllegalArgumentException(
                    "Requested cancellation requires a cause and timestamp"
            );
        }
        if (cancellationCallbackCount < 0) {
            throw new IllegalArgumentException(
                    "Cancellation callback count must not be negative"
            );
        }
    }

    public TaskCancellationView(
            CancellationStatus status,
            boolean cancellationRequested,
            Instant cancellationRequestedAt,
            CancellationCause cancellationCause,
            boolean futureCancellationAvailable,
            boolean futureCancellationAttempted,
            boolean futureCancellationAccepted,
            boolean interruptRequested,
            boolean interruptObserved,
            boolean cooperativeCancellationObserved,
            Instant cancelledAt,
            boolean gracePeriodExceeded,
            int cancellationCallbackCount,
            String cancellationError,
            TaskCancellationMode mode
    ) {
        this(
                status, cancellationRequested, cancellationRequestedAt,
                cancellationCause, futureCancellationAvailable,
                futureCancellationAttempted, futureCancellationAccepted,
                interruptRequested, interruptObserved,
                cooperativeCancellationObserved, cancelledAt,
                gracePeriodExceeded, cancellationCallbackCount,
                cancellationError, mode, ChildCancellationPolicy.NONE
        );
    }

    public static TaskCancellationView none(TaskCancellationMode mode) {
        return new TaskCancellationView(
                CancellationStatus.NOT_REQUESTED,
                false,
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                false,
                0,
                null,
                mode,
                ChildCancellationPolicy.NONE
        );
    }
}
