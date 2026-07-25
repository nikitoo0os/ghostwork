package io.nikitoo0os.scheduling;

import java.time.Duration;
import java.util.Objects;

public record ScheduleOptions(
        String name,
        ScheduleMetadata metadata,
        Duration lateThreshold,
        Duration longRunningThreshold
) {
    public ScheduleOptions {
        Objects.requireNonNull(name, "Schedule name must not be null");
        Objects.requireNonNull(metadata, "Schedule metadata must not be null");
        requireNonNegative(lateThreshold, "Late threshold");
        requirePositive(longRunningThreshold, "Long-running threshold");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Schedule name must not be blank");
        }
    }

    public static ScheduleOptions manual(String name) {
        return new ScheduleOptions(
                name,
                ScheduleMetadata.manual(name),
                Duration.ofMillis(100),
                Duration.ofMinutes(1)
        );
    }

    private static void requireNonNegative(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void requirePositive(Duration value, String name) {
        requireNonNegative(value, name);
        if (value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
