package io.nikitoo0os.event;

import io.nikitoo0os.OperationView;
import io.nikitoo0os.TaskView;

import java.util.Objects;

public record GhostWorkEvent(
        GhostWorkEventType type,
        OperationView operation,
        TaskView task,
        Throwable failure
) {
    public GhostWorkEvent {
        Objects.requireNonNull(type, "Event type must not be null");
        Objects.requireNonNull(operation, "Operation must not be null");
    }
}