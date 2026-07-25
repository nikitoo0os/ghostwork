package io.nikitoo0os.scheduling;

import io.nikitoo0os.GhostWork;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

public final class ExternalScheduleTracker {
    private final ScheduleDefinition definition;
    private final ScheduleOptions options;
    private final SchedulingCoordinator coordinator;

    ExternalScheduleTracker(
            ScheduleDefinition definition,
            ScheduleOptions options,
            SchedulingCoordinator coordinator
    ) {
        this.definition = Objects.requireNonNull(definition);
        this.options = Objects.requireNonNull(options);
        this.coordinator = Objects.requireNonNull(coordinator);
    }

    public static ExternalScheduleTracker create(
            GhostWork ghostWork,
            ScheduleRegistry registry,
            Clock clock,
            ScheduleOptions options,
            ScheduleType type,
            ScheduleTrigger trigger
    ) {
        SchedulingCoordinator coordinator = new SchedulingCoordinator(
                Objects.requireNonNull(ghostWork),
                Objects.requireNonNull(registry),
                Objects.requireNonNull(clock)
        );
        return new ExternalScheduleTracker(
                coordinator.create(
                        Objects.requireNonNull(options),
                        Objects.requireNonNull(type),
                        Objects.requireNonNull(trigger)
                ),
                options,
                coordinator
        );
    }

    public ScheduleId id() {
        return definition.id();
    }

    public void expect(Instant expectedAt) {
        coordinator.expect(definition, Objects.requireNonNull(expectedAt));
    }

    public void activate() {
        coordinator.activate(definition);
    }

    public void registrationFailed() {
        coordinator.registrationFailed(definition);
    }

    public void complete() {
        coordinator.complete(definition);
    }

    public void run(Runnable command) {
        coordinator.runContinuing(
                definition,
                options,
                Objects.requireNonNull(command)
        );
    }

    public void runOneTime(Runnable command) {
        coordinator.run(
                definition,
                options,
                Objects.requireNonNull(command)
        );
    }

    public <V> ScheduledFuture<V> track(ScheduledFuture<V> future) {
        return new ExternalTrackedScheduledFuture<>(
                Objects.requireNonNull(future),
                definition,
                coordinator
        );
    }
}
