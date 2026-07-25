package io.nikitoo0os.scheduling;

import io.nikitoo0os.OperationMetadata;

import java.time.Instant;

public record ScheduledExecutionMetadata(
        ScheduleId scheduleId,
        long executionNumber,
        Instant expectedStartAt
) implements OperationMetadata {
}
