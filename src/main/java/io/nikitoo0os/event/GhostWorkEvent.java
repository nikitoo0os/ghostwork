package io.nikitoo0os.event;

import io.nikitoo0os.OperationView;
import io.nikitoo0os.TaskView;
import io.nikitoo0os.CancellationCause;

import java.time.Instant;
import java.util.Objects;

public record GhostWorkEvent(
        GhostWorkEventType type,
        OperationView operation,
        TaskView task,
        Throwable failure,
        CancellationCause cancellationCause,
        Instant occurredAt
) {
    public GhostWorkEvent(
            GhostWorkEventType type,
            OperationView operation,
            TaskView task,
            Throwable failure
    ) {
        this(type, operation, task, failure, null, Instant.now());
    }

    public GhostWorkEvent {
        Objects.requireNonNull(type, "Event type must not be null");
        Objects.requireNonNull(operation, "Operation must not be null");
    }
}
