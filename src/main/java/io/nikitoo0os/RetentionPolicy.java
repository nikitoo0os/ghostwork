package io.nikitoo0os;

import java.time.Duration;
import java.util.Objects;

public record RetentionPolicy(
        int maxCompletedOperations,
        Duration completedOperationTtl,
        Duration cleanupInterval
) {
    public RetentionPolicy {
        if (maxCompletedOperations < 0) {
            throw new IllegalArgumentException(
                    "Maximum completed operations must not be negative"
            );
        }
        Objects.requireNonNull(
                completedOperationTtl,
                "Completed operation TTL must not be null"
        );
        Objects.requireNonNull(
                cleanupInterval,
                "Cleanup interval must not be null"
        );
        if (completedOperationTtl.isZero()
                || completedOperationTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "Completed operation TTL must be positive"
            );
        }
        if (cleanupInterval.isZero() || cleanupInterval.isNegative()) {
            throw new IllegalArgumentException(
                    "Cleanup interval must be positive"
            );
        }
    }

    public static RetentionPolicy defaults() {
        return new RetentionPolicy(
                10_000,
                Duration.ofHours(24),
                Duration.ofMinutes(5)
        );
    }
}
