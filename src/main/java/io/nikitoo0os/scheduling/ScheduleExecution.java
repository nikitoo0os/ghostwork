package io.nikitoo0os.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class ScheduleExecution {
    private final ScheduleId scheduleId;
    private final long executionNumber;
    private final Instant expectedStartAt;
    private ScheduleExecutionState state = ScheduleExecutionState.EXPECTED;
    private Instant actualStartAt;
    private Instant finishedAt;
    private UUID operationId;
    private UUID rootTaskId;
    private ScheduleTiming timing = ScheduleTiming.NOT_STARTED;
    private Set<Long> overlap = Set.of();
    private boolean longRunning;
    private Duration longRunningThreshold;
    private long missedExecutionsEstimate;
    private Throwable failure;

    ScheduleExecution(
            ScheduleId scheduleId,
            long executionNumber,
            Instant expectedStartAt
    ) {
        this.scheduleId = Objects.requireNonNull(scheduleId);
        this.executionNumber = executionNumber;
        this.expectedStartAt = Objects.requireNonNull(expectedStartAt);
    }

    void start(
            Instant actualStartAt,
            UUID operationId,
            UUID rootTaskId,
            boolean late,
            Set<Long> overlap,
            Duration longRunningThreshold,
            long missedExecutionsEstimate
    ) {
        requireState(ScheduleExecutionState.EXPECTED);
        this.actualStartAt = Objects.requireNonNull(actualStartAt);
        this.operationId = Objects.requireNonNull(operationId);
        this.rootTaskId = Objects.requireNonNull(rootTaskId);
        this.timing = late ? ScheduleTiming.LATE : ScheduleTiming.ON_TIME;
        this.overlap = Set.copyOf(overlap);
        this.longRunningThreshold = Objects.requireNonNull(longRunningThreshold);
        this.missedExecutionsEstimate = Math.max(0, missedExecutionsEstimate);
        state = ScheduleExecutionState.RUNNING;
    }

    void complete(Instant finishedAt) {
        finish(ScheduleExecutionState.COMPLETED, finishedAt, null);
    }

    void fail(Instant finishedAt, Throwable failure) {
        finish(
                ScheduleExecutionState.FAILED,
                finishedAt,
                Objects.requireNonNull(failure)
        );
    }

    void cancel(Instant finishedAt) {
        if (state != ScheduleExecutionState.EXPECTED
                && state != ScheduleExecutionState.RUNNING) {
            return;
        }
        this.finishedAt = Objects.requireNonNull(finishedAt);
        state = ScheduleExecutionState.CANCELLED;
    }

    boolean markLongRunning() {
        if (longRunning) {
            return false;
        }
        longRunning = true;
        return true;
    }

    boolean refreshLongRunning(Instant observedAt) {
        return state == ScheduleExecutionState.RUNNING
                && actualStartAt != null
                && observedAt.isAfter(actualStartAt.plus(longRunningThreshold))
                && markLongRunning();
    }

    ScheduleExecutionView view(Instant observedAt) {
        return new ScheduleExecutionView(
                scheduleId,
                executionNumber,
                state,
                expectedStartAt,
                actualStartAt,
                finishedAt,
                operationId,
                rootTaskId,
                timing,
                !overlap.isEmpty(),
                longRunning,
                missedExecutionsEstimate > 0,
                missedExecutionsEstimate,
                overlap,
                duration(observedAt),
                failure == null ? null : failure.getClass().getName(),
                failure == null ? null : failure.getMessage()
        );
    }

    Duration duration() {
        return duration(finishedAt == null ? actualStartAt : finishedAt);
    }

    private Duration duration(Instant observedAt) {
        if (actualStartAt == null) {
            return Duration.ZERO;
        }
        Instant end = finishedAt == null ? observedAt : finishedAt;
        if (end.isBefore(actualStartAt)) {
            return Duration.ZERO;
        }
        return Duration.between(actualStartAt, end);
    }

    ScheduleExecutionState state() {
        return state;
    }

    long executionNumber() {
        return executionNumber;
    }

    Instant expectedStartAt() {
        return expectedStartAt;
    }

    Instant finishedAt() {
        return finishedAt;
    }

    UUID operationId() {
        return operationId;
    }

    ScheduleTiming timing() {
        return timing;
    }

    private void finish(
            ScheduleExecutionState target,
            Instant finishedAt,
            Throwable failure
    ) {
        requireState(ScheduleExecutionState.RUNNING);
        this.finishedAt = Objects.requireNonNull(finishedAt);
        this.failure = failure;
        state = target;
    }

    private void requireState(ScheduleExecutionState expected) {
        if (state != expected) {
            throw new IllegalStateException(
                    "Execution cannot switch from " + state
                            + "; expected " + expected
            );
        }
    }
}
