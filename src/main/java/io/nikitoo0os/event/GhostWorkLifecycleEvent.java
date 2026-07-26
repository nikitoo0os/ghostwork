package io.nikitoo0os.event;

import io.nikitoo0os.CorrelationId;

import java.time.Instant;

public sealed interface GhostWorkLifecycleEvent permits
        OperationLifecycleEvent,
        TaskLifecycleEvent,
        ScheduleLifecycleEvent {

    long sequence();

    Instant occurredAt();

    CorrelationId correlationId();
}
