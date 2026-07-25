package io.nikitoo0os.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

final class ScheduleDefinition {
    private final ScheduleId id;
    private final String name;
    private final ScheduleType type;
    private final ScheduleTrigger trigger;
    private final ScheduleMetadata metadata;
    private final Instant createdAt;
    private final Deque<ScheduleExecution> executions = new ArrayDeque<>();
    private final Set<Long> activeExecutions = new LinkedHashSet<>();
    private ScheduleState state = ScheduleState.CREATED;
    private Instant activatedAt;
    private Instant terminalAt;
    private ScheduledFuture<?> future;
    private long nextExecutionNumber = 1;
    private long expected;
    private long started;
    private long completed;
    private long failed;
    private long cancelled;
    private long late;
    private long overlapping;
    private long longRunning;
    private long possiblyMissed;
    private Duration totalDuration = Duration.ZERO;
    private Duration maxDuration = Duration.ZERO;
    private Instant lastExecutionAt;

    ScheduleDefinition(
            String name,
            ScheduleType type,
            ScheduleTrigger trigger,
            ScheduleMetadata metadata,
            Instant createdAt
    ) {
        this.id = ScheduleId.random();
        this.name = requireText(name, "Schedule name");
        this.type = Objects.requireNonNull(type, "Schedule type must not be null");
        this.trigger = Objects.requireNonNull(trigger, "Schedule trigger must not be null");
        this.metadata = Objects.requireNonNull(metadata, "Schedule metadata must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created time must not be null");
    }

    synchronized ScheduleExecution expect(Instant expectedAt) {
        if (state != ScheduleState.CREATED && state != ScheduleState.ACTIVE) {
            return null;
        }
        ScheduleExecution execution = new ScheduleExecution(
                id,
                nextExecutionNumber++,
                expectedAt
        );
        executions.addLast(execution);
        expected = increment(expected);
        return execution;
    }

    synchronized ScheduleExecution claim(Instant expectedAt) {
        ScheduleExecution execution = executions.stream()
                .filter(item -> item.state() == ScheduleExecutionState.EXPECTED)
                .findFirst()
                .orElseGet(() -> expect(expectedAt));
        return execution;
    }

    synchronized Set<Long> start(
            ScheduleExecution execution,
            Instant actualStart,
            UUID operationId,
            UUID rootTaskId,
            Duration lateThreshold,
            Duration longRunningThreshold
    ) {
        Set<Long> overlap = Set.copyOf(activeExecutions);
        long missedEstimate = missedEstimate(execution, actualStart);
        execution.start(
                actualStart,
                operationId,
                rootTaskId,
                actualStart.isAfter(execution.expectedStartAt().plus(lateThreshold)),
                overlap,
                longRunningThreshold,
                missedEstimate
        );
        activeExecutions.add(execution.executionNumber());
        started = increment(started);
        if (execution.timing() == ScheduleTiming.LATE) {
            late = increment(late);
        }
        if (!overlap.isEmpty()) {
            overlapping = increment(overlapping);
        }
        possiblyMissed = safeAdd(possiblyMissed, missedEstimate);
        lastExecutionAt = actualStart;
        return overlap;
    }

    synchronized void finish(
            ScheduleExecution execution,
            Instant finishedAt,
            Throwable failure,
            boolean cancelledExecution,
            Duration longRunningThreshold
    ) {
        activeExecutions.remove(execution.executionNumber());
        if (cancelledExecution) {
            execution.cancel(finishedAt);
            cancelled = increment(cancelled);
        } else if (failure == null) {
            execution.complete(finishedAt);
            completed = increment(completed);
        } else {
            execution.fail(finishedAt, failure);
            failed = increment(failed);
        }
        Duration duration = execution.duration();
        totalDuration = safePlus(totalDuration, duration);
        if (duration.compareTo(maxDuration) > 0) {
            maxDuration = duration;
        }
        if (duration.compareTo(longRunningThreshold) > 0
                && execution.markLongRunning()) {
            longRunning = increment(longRunning);
        }
    }

    synchronized void activate(ScheduledFuture<?> scheduledFuture, Instant now) {
        if (state != ScheduleState.CREATED) {
            throw new IllegalStateException("Only a created schedule can be activated");
        }
        future = Objects.requireNonNull(scheduledFuture);
        activatedAt = Objects.requireNonNull(now);
        state = ScheduleState.ACTIVE;
    }

    synchronized void activate(Instant now) {
        if (state != ScheduleState.CREATED) {
            throw new IllegalStateException("Only a created schedule can be activated");
        }
        activatedAt = Objects.requireNonNull(now);
        state = ScheduleState.ACTIVE;
    }

    synchronized void failRegistration(Instant now) {
        if (state == ScheduleState.CREATED) {
            state = ScheduleState.FAILED;
            terminalAt = now;
            cancelExpected(now);
        }
    }

    synchronized void complete(Instant now) {
        if (state == ScheduleState.ACTIVE) {
            state = ScheduleState.COMPLETED;
            terminalAt = now;
            future = null;
            cancelExpected(now);
        }
    }

    synchronized void fail(Instant now) {
        if (state == ScheduleState.ACTIVE) {
            state = ScheduleState.FAILED;
            terminalAt = now;
            future = null;
            cancelExpected(now);
        }
    }

    synchronized boolean cancel(Instant now) {
        if (state != ScheduleState.CREATED && state != ScheduleState.ACTIVE) {
            return false;
        }
        state = ScheduleState.CANCELLED;
        terminalAt = now;
        future = null;
        cancelExpected(now);
        return true;
    }

    synchronized void reconcileDelegateCancellation(Instant now) {
        if (state == ScheduleState.ACTIVE
                && future != null
                && future.isCancelled()) {
            state = ScheduleState.CANCELLED;
            terminalAt = now;
            future = null;
            cancelExpected(now);
        }
    }

    synchronized boolean acceptsExecutions() {
        return state == ScheduleState.ACTIVE;
    }

    synchronized List<UUID> activeOperationIds() {
        return executions.stream()
                .filter(item -> item.state() == ScheduleExecutionState.RUNNING)
                .map(ScheduleExecution::operationId)
                .filter(Objects::nonNull)
                .toList();
    }

    synchronized long activeExecutionCount() {
        return activeExecutions.size();
    }

    synchronized long expectedExecutionCount() {
        return executions.stream()
                .filter(item -> item.state() == ScheduleExecutionState.EXPECTED)
                .count();
    }

    synchronized List<UUID> cleanup(
            ScheduleRetentionPolicy policy,
            Instant now
    ) {
        List<UUID> releasedOperations = new ArrayList<>();
        Instant cutoff = now.minus(policy.executionTtl());
        while (!executions.isEmpty()) {
            ScheduleExecution first = executions.peekFirst();
            boolean overLimit = executions.size() > policy.maxExecutionsPerSchedule();
            boolean expired = first.finishedAt() != null
                    && !first.finishedAt().isAfter(cutoff);
            if (first.state() == ScheduleExecutionState.RUNNING
                    || first.state() == ScheduleExecutionState.EXPECTED
                    || (!overLimit && !expired)) {
                break;
            }
            ScheduleExecution removed = executions.removeFirst();
            if (removed.operationId() != null) {
                releasedOperations.add(removed.operationId());
            }
        }
        return List.copyOf(releasedOperations);
    }

    synchronized ScheduleView view() {
        return new ScheduleView(
                id,
                name,
                type,
                state,
                trigger,
                metadata,
                createdAt,
                activatedAt,
                terminalAt,
                statistics()
        );
    }

    synchronized List<ScheduleExecutionView> executionViews(Instant observedAt) {
        refreshLongRunning(observedAt);
        List<ScheduleExecutionView> result = new ArrayList<>(executions.size());
        executions.descendingIterator().forEachRemaining(
                execution -> result.add(execution.view(observedAt))
        );
        return List.copyOf(result);
    }

    synchronized void refreshLongRunning(Instant observedAt) {
        for (ScheduleExecution execution : executions) {
            if (execution.refreshLongRunning(observedAt)) {
                longRunning = increment(longRunning);
            }
        }
    }

    synchronized ScheduleStatistics statistics() {
        return new ScheduleStatistics(
                expected,
                started,
                completed,
                failed,
                cancelled,
                late,
                overlapping,
                longRunning,
                possiblyMissed,
                false,
                totalDuration,
                maxDuration,
                lastExecutionAt
        );
    }

    ScheduleId id() {
        return id;
    }

    String name() {
        return name;
    }

    ScheduleType type() {
        return type;
    }

    ScheduleTrigger trigger() {
        return trigger;
    }

    private void cancelExpected(Instant now) {
        executions.stream()
                .filter(item -> item.state() == ScheduleExecutionState.EXPECTED)
                .forEach(item -> {
                    item.cancel(now);
                    cancelled = increment(cancelled);
                });
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1;
    }

    private long missedEstimate(
            ScheduleExecution execution,
            Instant actualStart
    ) {
        if (!(trigger instanceof ScheduleTrigger.FixedRate fixedRate)
                || !actualStart.isAfter(execution.expectedStartAt())) {
            return 0;
        }
        try {
            long periodNanos = fixedRate.period().toNanos();
            long delayNanos = Duration.between(
                    execution.expectedStartAt(),
                    actualStart
            ).toNanos();
            return periodNanos <= 0 ? 0 : delayNanos / periodNanos;
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static long safeAdd(long left, long right) {
        if (right <= 0) {
            return left;
        }
        return Long.MAX_VALUE - left < right
                ? Long.MAX_VALUE
                : left + right;
    }

    private static Duration safePlus(Duration left, Duration right) {
        try {
            return left.plus(right);
        } catch (ArithmeticException overflow) {
            return Duration.ofSeconds(Long.MAX_VALUE, 999_999_999);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
