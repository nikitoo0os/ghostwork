package io.nikitoo0os.scheduling;

import java.time.Instant;

public record SchedulerShutdownView(
        SchedulerShutdownMethod method,
        Instant requestedAt,
        long activeExecutions,
        long expectedExecutions,
        int returnedQueuedTasks,
        boolean terminated,
        Instant terminationObservedAt
) {
}
