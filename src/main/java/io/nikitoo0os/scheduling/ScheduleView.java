package io.nikitoo0os.scheduling;

import java.time.Instant;

public record ScheduleView(
        ScheduleId id,
        String name,
        ScheduleType type,
        ScheduleState state,
        ScheduleTrigger trigger,
        ScheduleMetadata metadata,
        Instant createdAt,
        Instant activatedAt,
        Instant terminalAt,
        ScheduleStatistics statistics
) {
}
