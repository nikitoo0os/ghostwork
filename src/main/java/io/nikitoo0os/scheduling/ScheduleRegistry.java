package io.nikitoo0os.scheduling;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.time.Clock;
import io.nikitoo0os.event.GhostWorkEventPublisher;

public final class ScheduleRegistry {
    private final ConcurrentMap<ScheduleId, ScheduleDefinition> schedules =
            new ConcurrentHashMap<>();
    private final Consumer<UUID> retainOperation;
    private final Consumer<UUID> releaseOperation;
    private final Clock clock;
    private final GhostWorkEventPublisher lifecycleEvents;
    private final Function<UUID, io.nikitoo0os.CorrelationId>
            correlationResolver;
    private final java.util.concurrent.CopyOnWriteArrayList<ScheduleEventListener>
            listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public ScheduleRegistry() {
        this(ignored -> {
        }, ignored -> {
        }, Clock.systemUTC(), new GhostWorkEventPublisher(), ignored -> null);
    }

    public ScheduleRegistry(
            Consumer<UUID> retainOperation,
            Consumer<UUID> releaseOperation,
            Clock clock
    ) {
        this(
                retainOperation,
                releaseOperation,
                clock,
                new GhostWorkEventPublisher(clock),
                ignored -> null
        );
    }

    public ScheduleRegistry(
            Consumer<UUID> retainOperation,
            Consumer<UUID> releaseOperation,
            Clock clock,
            GhostWorkEventPublisher lifecycleEvents
    ) {
        this(
                retainOperation,
                releaseOperation,
                clock,
                lifecycleEvents,
                ignored -> null
        );
    }

    public ScheduleRegistry(
            Consumer<UUID> retainOperation,
            Consumer<UUID> releaseOperation,
            Clock clock,
            GhostWorkEventPublisher lifecycleEvents,
            Function<UUID, io.nikitoo0os.CorrelationId>
                    correlationResolver
    ) {
        this.retainOperation = Objects.requireNonNull(retainOperation);
        this.releaseOperation = Objects.requireNonNull(releaseOperation);
        this.clock = Objects.requireNonNull(clock);
        this.lifecycleEvents = Objects.requireNonNull(lifecycleEvents);
        this.correlationResolver = Objects.requireNonNull(
                correlationResolver
        );
    }

    ScheduleDefinition register(ScheduleDefinition definition) {
        Objects.requireNonNull(definition, "Schedule definition must not be null");
        if (schedules.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException(
                    "Schedule already exists: " + definition.id()
            );
        }
        return definition;
    }

    ScheduleDefinition definition(ScheduleId id) {
        Objects.requireNonNull(id, "Schedule id must not be null");
        ScheduleDefinition definition = schedules.get(id);
        if (definition == null) {
            throw new NoSuchElementException("Schedule not found: " + id);
        }
        return definition;
    }

    public ScheduleView find(ScheduleId id) {
        ScheduleDefinition definition = definition(id);
        definition.refreshLongRunning(clock.instant());
        return definition.view();
    }

    public List<ScheduleView> find(ScheduleQuery query) {
        Objects.requireNonNull(query, "Schedule query must not be null");
        Instant observedAt = clock.instant();
        return schedules.values().stream()
                .peek(definition -> definition.refreshLongRunning(observedAt))
                .map(ScheduleDefinition::view)
                .filter(view -> query.states().isEmpty()
                        || query.states().contains(view.state()))
                .sorted(Comparator.comparing(ScheduleView::createdAt).reversed())
                .skip(query.offset())
                .limit(query.limit())
                .toList();
    }

    public long count(java.util.Set<ScheduleState> states) {
        java.util.Set<ScheduleState> filter = states == null
                ? java.util.Set.of()
                : java.util.Set.copyOf(states);
        return schedules.values().stream()
                .map(ScheduleDefinition::view)
                .filter(view -> filter.isEmpty() || filter.contains(view.state()))
                .count();
    }

    public List<ScheduleExecutionView> executions(
            ScheduleId id,
            ScheduleExecutionQuery query
    ) {
        Objects.requireNonNull(query, "Execution query must not be null");
        return definition(id).executionViews(clock.instant()).stream()
                .filter(view -> query.states().isEmpty()
                        || query.states().contains(view.state()))
                .skip(query.offset())
                .limit(query.limit())
                .toList();
    }

    public long countExecutions(
            ScheduleId id,
            java.util.Set<ScheduleExecutionState> states
    ) {
        java.util.Set<ScheduleExecutionState> filter = states == null
                ? java.util.Set.of()
                : java.util.Set.copyOf(states);
        return definition(id).executionViews(clock.instant()).stream()
                .filter(view -> filter.isEmpty() || filter.contains(view.state()))
                .count();
    }

    public int cleanup(
            ScheduleRetentionPolicy policy,
            Instant now
    ) {
        Objects.requireNonNull(policy);
        Objects.requireNonNull(now);
        int removed = 0;
        for (ScheduleDefinition definition : schedules.values()) {
            List<UUID> released = definition.cleanup(policy, now);
            released.forEach(releaseOperation);
            removed += released.size();
        }
        return removed;
    }

    void retainExecutionOperation(UUID operationId) {
        retainOperation.accept(Objects.requireNonNull(operationId));
    }

    public void addListener(ScheduleEventListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(ScheduleEventListener listener) {
        listeners.remove(Objects.requireNonNull(listener));
    }

    void publish(
            ScheduleEventType type,
            ScheduleDefinition definition,
            ScheduleExecution execution,
            Throwable failure
    ) {
        Instant now = clock.instant();
        ScheduleEvent event = new ScheduleEvent(
                type,
                definition.view(),
                execution == null ? null : execution.view(now),
                now,
                failure == null ? null : failure.getClass().getName(),
                failure == null ? null : failure.getMessage()
        );
        for (ScheduleEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Throwable ignored) {
                // Diagnostic listeners must not affect scheduling semantics.
            }
        }
        io.nikitoo0os.CorrelationId correlation =
                execution == null || execution.operationId() == null
                        ? null
                        : correlationResolver.apply(execution.operationId());
        lifecycleEvents.publishSchedule(event, correlation);
    }
}
