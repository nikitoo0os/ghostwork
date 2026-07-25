package io.nikitoo0os.scheduling;

import java.util.Set;

public record ScheduleQuery(
        Set<ScheduleState> states,
        int offset,
        int limit
) {
    public ScheduleQuery {
        states = states == null ? Set.of() : Set.copyOf(states);
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
    }

    public static ScheduleQuery firstPage() {
        return new ScheduleQuery(Set.of(), 0, 100);
    }
}
