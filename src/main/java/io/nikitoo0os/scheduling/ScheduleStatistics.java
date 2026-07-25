package io.nikitoo0os.scheduling;

import java.time.Duration;
import java.time.Instant;

public record ScheduleStatistics(
        long expectedExecutions,
        long startedExecutions,
        long completedExecutions,
        long failedExecutions,
        long cancelledExecutions,
        long lateExecutions,
        long overlappingExecutions,
        long longRunningExecutions,
        long possiblyMissedExecutions,
        boolean missedEstimateExact,
        Duration totalDuration,
        Duration maxDuration,
        Instant lastExecutionAt
) {
    public static ScheduleStatistics empty() {
        return new ScheduleStatistics(
                0, 0, 0, 0, 0, 0, 0, 0, 0, false,
                Duration.ZERO, Duration.ZERO, null
        );
    }
}
