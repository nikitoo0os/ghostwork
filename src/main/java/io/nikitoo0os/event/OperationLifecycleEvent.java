package io.nikitoo0os.event;

import io.nikitoo0os.CancellationCause;
import io.nikitoo0os.CorrelationId;
import io.nikitoo0os.OperationView;

import java.time.Instant;
import java.util.Objects;

public record OperationLifecycleEvent(
        long sequence,
        Instant occurredAt,
        CorrelationId correlationId,
        GhostWorkEventType type,
        OperationView operation,
        ErrorInfo error,
        CancellationCause cancellationCause
) implements GhostWorkLifecycleEvent {
    public OperationLifecycleEvent {
        if (sequence < 1) {
            throw new IllegalArgumentException("Sequence must be positive");
        }
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(operation);
    }
}
