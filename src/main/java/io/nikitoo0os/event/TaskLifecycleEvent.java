package io.nikitoo0os.event;

import io.nikitoo0os.CancellationCause;
import io.nikitoo0os.CorrelationId;
import io.nikitoo0os.OperationView;
import io.nikitoo0os.TaskView;

import java.time.Instant;
import java.util.Objects;

public record TaskLifecycleEvent(
        long sequence,
        Instant occurredAt,
        CorrelationId correlationId,
        GhostWorkEventType type,
        OperationView operation,
        TaskView task,
        ErrorInfo error,
        CancellationCause cancellationCause
) implements GhostWorkLifecycleEvent {
    public TaskLifecycleEvent {
        if (sequence < 1) {
            throw new IllegalArgumentException("Sequence must be positive");
        }
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(operation);
        Objects.requireNonNull(task);
    }
}
