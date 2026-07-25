package io.nikitoo0os.scheduling;

import java.time.Instant;
import java.util.Objects;

public record ScheduleEvent(
        ScheduleEventType type,
        ScheduleView schedule,
        ScheduleExecutionView execution,
        Instant occurredAt,
        String failureType,
        String failureMessage
) {
    public ScheduleEvent {
        Objects.requireNonNull(type, "Schedule event type must not be null");
        Objects.requireNonNull(schedule, "Schedule view must not be null");
        Objects.requireNonNull(occurredAt, "Event time must not be null");
    }
}
