package io.nikitoo0os.scheduling;

import java.util.Map;
import java.util.Objects;

public record ScheduleMetadata(
        String logicalKey,
        String source,
        Map<String, String> attributes
) {
    public ScheduleMetadata {
        Objects.requireNonNull(logicalKey, "Logical key must not be null");
        Objects.requireNonNull(source, "Schedule source must not be null");
        if (logicalKey.isBlank()) {
            throw new IllegalArgumentException("Logical key must not be blank");
        }
        if (source.isBlank()) {
            throw new IllegalArgumentException("Schedule source must not be blank");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static ScheduleMetadata manual(String logicalKey) {
        return new ScheduleMetadata(logicalKey, "MANUAL_API", Map.of());
    }
}
