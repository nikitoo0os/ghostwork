package io.nikitoo0os.scheduling;

import java.util.Set;

public record ScheduleExecutionQuery(
        Set<ScheduleExecutionState> states,
        int offset,
        int limit
) {
    public ScheduleExecutionQuery {
        states = states == null ? Set.of() : Set.copyOf(states);
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
    }

    public static ScheduleExecutionQuery recent() {
        return new ScheduleExecutionQuery(Set.of(), 0, 100);
    }
}
