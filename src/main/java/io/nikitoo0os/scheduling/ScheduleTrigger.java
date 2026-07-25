package io.nikitoo0os.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public sealed interface ScheduleTrigger permits
        ScheduleTrigger.OneTime,
        ScheduleTrigger.FixedRate,
        ScheduleTrigger.FixedDelay,
        ScheduleTrigger.Cron,
        ScheduleTrigger.Custom {

    record OneTime(Instant scheduledAt) implements ScheduleTrigger {
        public OneTime {
            Objects.requireNonNull(scheduledAt, "Scheduled time must not be null");
        }
    }

    record FixedRate(
            Instant firstExecutionAt,
            Duration period
    ) implements ScheduleTrigger {
        public FixedRate {
            Objects.requireNonNull(firstExecutionAt, "First execution must not be null");
            requirePositive(period, "Fixed-rate period");
        }
    }

    record FixedDelay(
            Instant firstExecutionAt,
            Duration delay
    ) implements ScheduleTrigger {
        public FixedDelay {
            Objects.requireNonNull(firstExecutionAt, "First execution must not be null");
            requirePositive(delay, "Fixed delay");
        }
    }

    record Cron(
            String expression,
            ZoneId zone
    ) implements ScheduleTrigger {
        public Cron {
            Objects.requireNonNull(expression, "Cron expression must not be null");
            Objects.requireNonNull(zone, "Cron zone must not be null");
            if (expression.isBlank()) {
                throw new IllegalArgumentException("Cron expression must not be blank");
            }
        }
    }

    record Custom(String description) implements ScheduleTrigger {
        public Custom {
            Objects.requireNonNull(description, "Trigger description must not be null");
            if (description.isBlank()) {
                throw new IllegalArgumentException("Trigger description must not be blank");
            }
        }
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
