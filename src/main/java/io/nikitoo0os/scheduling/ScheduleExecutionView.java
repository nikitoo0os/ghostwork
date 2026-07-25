package io.nikitoo0os.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ScheduleExecutionView(
        ScheduleId scheduleId,
        long executionNumber,
        ScheduleExecutionState state,
        Instant expectedStartAt,
        Instant actualStartAt,
        Instant finishedAt,
        UUID operationId,
        UUID rootTaskId,
        ScheduleTiming timing,
        boolean overlapping,
        boolean longRunning,
        boolean possiblyMissed,
        long missedExecutionsEstimate,
        Set<Long> overlappingExecutionNumbers,
        Duration duration,
        String failureType,
        String failureMessage
) {
    public ScheduleExecutionView {
        overlappingExecutionNumbers = Set.copyOf(overlappingExecutionNumbers);
    }
}
