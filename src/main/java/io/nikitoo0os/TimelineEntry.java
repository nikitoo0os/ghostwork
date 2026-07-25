package io.nikitoo0os;

import java.time.Instant;
import java.util.UUID;

public record TimelineEntry(
        Instant timestamp,
        String type,
        String description,
        UUID taskId
) {
}
