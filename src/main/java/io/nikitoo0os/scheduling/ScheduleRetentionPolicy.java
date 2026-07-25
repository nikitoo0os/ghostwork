package io.nikitoo0os.scheduling;

import java.time.Duration;
import java.util.Objects;

public record ScheduleRetentionPolicy(
        int maxExecutionsPerSchedule,
        Duration executionTtl
) {
    public ScheduleRetentionPolicy {
        if (maxExecutionsPerSchedule < 1) {
            throw new IllegalArgumentException(
                    "Maximum executions per schedule must be positive"
            );
        }
        Objects.requireNonNull(executionTtl, "Execution TTL must not be null");
        if (executionTtl.isZero() || executionTtl.isNegative()) {
            throw new IllegalArgumentException("Execution TTL must be positive");
        }
    }

    public static ScheduleRetentionPolicy defaults() {
        return new ScheduleRetentionPolicy(1_000, Duration.ofDays(1));
    }
}
