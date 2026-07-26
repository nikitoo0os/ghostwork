package io.nikitoo0os.event;

import io.nikitoo0os.CorrelationId;
import io.nikitoo0os.scheduling.ScheduleEvent;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class GhostWorkEventPublisher {

    private final List<GhostWorkEventListener> listeners =
            new CopyOnWriteArrayList<>();
    private final List<GhostWorkLifecycleEventListener> lifecycleListeners =
            new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Clock clock;

    public GhostWorkEventPublisher() {
        this(Clock.systemUTC());
    }

    public GhostWorkEventPublisher(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    public void addListener(GhostWorkEventListener listener) {
        listeners.add(
                Objects.requireNonNull(
                        listener,
                        "Listener must not be null"
                )
        );
    }

    public void removeListener(GhostWorkEventListener listener) {
        listeners.remove(
                Objects.requireNonNull(
                        listener,
                        "Listener must not be null"
                )
        );
    }

    public void addLifecycleListener(
            GhostWorkLifecycleEventListener listener
    ) {
        lifecycleListeners.add(Objects.requireNonNull(listener));
    }

    public void removeLifecycleListener(
            GhostWorkLifecycleEventListener listener
    ) {
        lifecycleListeners.remove(Objects.requireNonNull(listener));
    }

    public void publishOperationStarted(
            io.nikitoo0os.OperationView operation
    ) {
        Objects.requireNonNull(operation, "Operation must not be null");
        publishLifecycle(new OperationLifecycleEvent(
                sequence.incrementAndGet(),
                clock.instant(),
                operation.correlationId(),
                GhostWorkEventType.OPERATION_STARTED,
                operation,
                null,
                null
        ));
    }

    public void publishTaskCreated(
            io.nikitoo0os.OperationView operation,
            io.nikitoo0os.TaskView task
    ) {
        Objects.requireNonNull(operation, "Operation must not be null");
        Objects.requireNonNull(task, "Task must not be null");
        publishLifecycle(new TaskLifecycleEvent(
                sequence.incrementAndGet(),
                clock.instant(),
                operation.correlationId(),
                GhostWorkEventType.TASK_CREATED,
                operation,
                task,
                null,
                null
        ));
    }

    public void publish(GhostWorkEvent event) {
        Objects.requireNonNull(event, "Event must not be null");
        publishLifecycle(toLifecycleEvent(event));

        for (GhostWorkEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Throwable ignored) {
                // Listener failures must not break GhostWork lifecycle.
            }
        }
    }

    public void publishSchedule(ScheduleEvent event) {
        publishSchedule(event, null);
    }

    public void publishSchedule(
            ScheduleEvent event,
            CorrelationId correlationId
    ) {
        Objects.requireNonNull(event, "Schedule event must not be null");
        CorrelationId resolved = correlationId == null
                ? correlation(event)
                : correlationId;
        publishLifecycle(new ScheduleLifecycleEvent(
                sequence.incrementAndGet(),
                clock.instant(),
                resolved,
                event.type(),
                event.schedule(),
                event.execution(),
                event.failureType() == null
                        ? null
                        : new ErrorInfo(
                                event.failureType(),
                                bounded(event.failureMessage())
                        )
        ));
    }

    private GhostWorkLifecycleEvent toLifecycleEvent(GhostWorkEvent event) {
        long next = sequence.incrementAndGet();
        if (event.task() == null) {
            return new OperationLifecycleEvent(
                    next,
                    clock.instant(),
                    event.operation().correlationId(),
                    event.type(),
                    event.operation(),
                    ErrorInfo.from(event.failure()),
                    event.cancellationCause()
            );
        }
        return new TaskLifecycleEvent(
                next,
                clock.instant(),
                event.operation().correlationId(),
                event.type(),
                event.operation(),
                event.task(),
                ErrorInfo.from(event.failure()),
                event.cancellationCause()
        );
    }

    private void publishLifecycle(GhostWorkLifecycleEvent event) {
        for (GhostWorkLifecycleEventListener listener : lifecycleListeners) {
            try {
                listener.onEvent(event);
            } catch (Throwable ignored) {
                // Telemetry listeners must not affect user execution.
            }
        }
    }

    private static CorrelationId correlation(ScheduleEvent event) {
        if (event.execution() != null
                && event.execution().operationId() != null) {
            return new CorrelationId(
                    event.execution().operationId().toString()
            );
        }
        return new CorrelationId(event.schedule().id().value().toString());
    }

    private static String bounded(String message) {
        if (message == null
                || message.length() <= ErrorInfo.MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, ErrorInfo.MAX_MESSAGE_LENGTH);
    }
}
