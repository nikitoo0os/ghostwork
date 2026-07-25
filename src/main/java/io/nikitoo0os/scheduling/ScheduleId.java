package io.nikitoo0os.scheduling;

import java.util.Objects;
import java.util.UUID;

public record ScheduleId(UUID value) {
    public ScheduleId {
        Objects.requireNonNull(value, "Schedule id must not be null");
    }

    public static ScheduleId random() {
        return new ScheduleId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
