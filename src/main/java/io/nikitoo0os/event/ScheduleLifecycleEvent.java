package io.nikitoo0os.event;

import io.nikitoo0os.CorrelationId;
import io.nikitoo0os.scheduling.ScheduleEventType;
import io.nikitoo0os.scheduling.ScheduleExecutionView;
import io.nikitoo0os.scheduling.ScheduleView;

import java.time.Instant;
import java.util.Objects;

public record ScheduleLifecycleEvent(
        long sequence,
        Instant occurredAt,
        CorrelationId correlationId,
        ScheduleEventType type,
        ScheduleView schedule,
        ScheduleExecutionView execution,
        ErrorInfo error
) implements GhostWorkLifecycleEvent {
    public ScheduleLifecycleEvent {
        if (sequence < 1) {
            throw new IllegalArgumentException("Sequence must be positive");
        }
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(schedule);
    }
}
