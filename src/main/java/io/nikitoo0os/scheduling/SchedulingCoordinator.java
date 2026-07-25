package io.nikitoo0os.scheduling;

import io.nikitoo0os.CancellationCause;
import io.nikitoo0os.GhostWork;
import io.nikitoo0os.GhostWorkContext;
import io.nikitoo0os.OperationHandle;
import io.nikitoo0os.OperationScope;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;

final class SchedulingCoordinator {
    private final GhostWork ghostWork;
    private final ScheduleRegistry registry;
    private final Clock clock;

    SchedulingCoordinator(
            GhostWork ghostWork,
            ScheduleRegistry registry,
            Clock clock
    ) {
        this.ghostWork = Objects.requireNonNull(ghostWork);
        this.registry = Objects.requireNonNull(registry);
        this.clock = Objects.requireNonNull(clock);
    }

    ScheduleDefinition create(
            ScheduleOptions options,
            ScheduleType type,
            ScheduleTrigger trigger
    ) {
        ScheduleDefinition definition = registry.register(new ScheduleDefinition(
                options.name(),
                type,
                trigger,
                options.metadata(),
                clock.instant()
        ));
        registry.publish(
                ScheduleEventType.SCHEDULE_CREATED,
                definition,
                null,
                null
        );
        return definition;
    }

    void activate(
            ScheduleDefinition definition,
            ScheduledFuture<?> future
    ) {
        definition.activate(future, clock.instant());
        registry.publish(
                ScheduleEventType.SCHEDULE_ACTIVATED,
                definition,
                null,
                null
        );
    }

    void activate(ScheduleDefinition definition) {
        definition.activate(clock.instant());
        registry.publish(
                ScheduleEventType.SCHEDULE_ACTIVATED,
                definition,
                null,
                null
        );
    }

    void registrationFailed(ScheduleDefinition definition) {
        definition.failRegistration(clock.instant());
        registry.publish(
                ScheduleEventType.SCHEDULE_FAILED,
                definition,
                null,
                null
        );
    }

    void complete(ScheduleDefinition definition) {
        definition.complete(clock.instant());
        registry.publish(
                ScheduleEventType.SCHEDULE_COMPLETED,
                definition,
                null,
                null
        );
    }

    ScheduleExecution expect(
            ScheduleDefinition definition,
            Instant expectedAt
    ) {
        ScheduleExecution execution = definition.expect(expectedAt);
        if (execution != null) {
            registry.publish(
                    ScheduleEventType.EXECUTION_EXPECTED,
                    definition,
                    execution,
                    null
            );
        }
        return execution;
    }

    void run(
            ScheduleDefinition definition,
            ScheduleOptions options,
            Runnable command
    ) {
        try {
            call(definition, options, () -> {
                command.run();
                return null;
            });
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception impossibleCheckedFailure) {
            throw new IllegalStateException(impossibleCheckedFailure);
        }
    }

    void runContinuing(
            ScheduleDefinition definition,
            ScheduleOptions options,
            Runnable command
    ) {
        try {
            call(definition, options, () -> {
                command.run();
                return null;
            }, false);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception impossibleCheckedFailure) {
            throw new IllegalStateException(impossibleCheckedFailure);
        }
    }

    <V> V call(
            ScheduleDefinition definition,
            ScheduleOptions options,
            Callable<V> command
    ) throws Exception {
        return call(definition, options, command, true);
    }

    private <V> V call(
            ScheduleDefinition definition,
            ScheduleOptions options,
            Callable<V> command,
            boolean stopOnFailure
    ) throws Exception {
        if (!definition.acceptsExecutions()) {
            throw new CancellationException(
                    "Schedule is no longer active: " + definition.id()
            );
        }
        ScheduleExecution execution = definition.claim(clock.instant());
        OperationHandle operation = ghostWork.startOperation(
                definition.name() + " [execution #"
                        + execution.executionNumber() + "]",
                new ScheduledExecutionMetadata(
                        definition.id(),
                        execution.executionNumber(),
                        execution.expectedStartAt()
                )
        );

        Throwable businessFailure = null;
        try (OperationScope ignored = operation.openScope()) {
            return ghostWork.executor().callTask(
                    definition.name() + " scheduled invocation",
                    () -> {
                        definition.start(
                                execution,
                                clock.instant(),
                                operation.id(),
                                GhostWorkContext.currentTaskId().orElseThrow(),
                                options.lateThreshold(),
                                options.longRunningThreshold()
                        );
                        registry.retainExecutionOperation(operation.id());
                        registry.publish(
                                ScheduleEventType.EXECUTION_STARTED,
                                definition,
                                execution,
                                null
                        );
                        try {
                            return command.call();
                        } catch (Throwable failure) {
                            throw failure;
                        }
                    }
            );
        } catch (Throwable failure) {
            businessFailure = failure;
            finishExecution(
                    definition,
                    execution,
                    operation,
                    options,
                    failure,
                    stopOnFailure
            );
            if (failure instanceof Exception exception) {
                throw exception;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(failure);
        } finally {
            if (businessFailure == null) {
                finishExecution(
                        definition,
                        execution,
                        operation,
                        options,
                        null,
                        stopOnFailure
                );
            }
        }
    }

    boolean cancel(
            ScheduleDefinition definition,
            boolean mayInterruptIfRunning
    ) {
        boolean changed = definition.cancel(clock.instant());
        if (changed && mayInterruptIfRunning) {
            definition.activeOperationIds().forEach(operationId ->
                    ghostWork.cancelOperation(
                            operationId,
                            CancellationCause.SCHEDULE_CANCELLED
                    )
            );
        }
        if (changed) {
            registry.publish(
                    ScheduleEventType.SCHEDULE_CANCELLED,
                    definition,
                    null,
                    null
            );
        }
        return changed;
    }

    void shutdown() {
        registry.find(new ScheduleQuery(
                        java.util.Set.of(ScheduleState.ACTIVE),
                        0,
                        Integer.MAX_VALUE
                ))
                .forEach(view -> {
                    ScheduleDefinition definition = registry.definition(view.id());
                    if (definition.cancel(clock.instant())) {
                        definition.activeOperationIds().forEach(operationId ->
                                ghostWork.cancelOperation(
                                        operationId,
                                        CancellationCause.APPLICATION_SHUTDOWN
                                )
                        );
                    }
                });
    }

    void reconcileDelegateCancellation(ScheduleDefinition definition) {
        definition.reconcileDelegateCancellation(clock.instant());
    }

    private void finishExecution(
            ScheduleDefinition definition,
            ScheduleExecution execution,
            OperationHandle operation,
            ScheduleOptions options,
            Throwable failure,
            boolean stopOnFailure
    ) {
        Instant finishedAt = clock.instant();
        boolean cancelled = definition.view().state() == ScheduleState.CANCELLED
                && operation.view().state()
                == io.nikitoo0os.entity.enums.OperationState.CANCELLED;
        try {
            definition.finish(
                    execution,
                    finishedAt,
                    failure,
                    cancelled,
                    options.longRunningThreshold()
            );
            registry.publish(
                    cancelled
                            ? ScheduleEventType.EXECUTION_CANCELLED
                            : failure == null
                                    ? ScheduleEventType.EXECUTION_COMPLETED
                                    : ScheduleEventType.EXECUTION_FAILED,
                    definition,
                    execution,
                    failure
            );
            if (cancelled) {
                operation.cancel(CancellationCause.SCHEDULE_CANCELLED);
                return;
            }
            if (failure == null) {
                operation.complete();
                scheduleNext(definition, execution, finishedAt);
                if (definition.type() == ScheduleType.ONE_TIME) {
                    definition.complete(finishedAt);
                    registry.publish(
                            ScheduleEventType.SCHEDULE_COMPLETED,
                            definition,
                            null,
                            null
                    );
                }
            } else {
                operation.fail(failure);
                if (stopOnFailure) {
                    definition.fail(finishedAt);
                    registry.publish(
                            ScheduleEventType.SCHEDULE_FAILED,
                            definition,
                            null,
                            failure
                    );
                } else {
                    scheduleNext(definition, execution, finishedAt);
                }
            }
        } catch (Throwable trackingFailure) {
            if (failure != null) {
                failure.addSuppressed(trackingFailure);
                return;
            }
            throw trackingFailure;
        }
    }

    private void scheduleNext(
            ScheduleDefinition definition,
            ScheduleExecution execution,
            Instant finishedAt
    ) {
        if (!definition.acceptsExecutions()) {
            return;
        }
        if (definition.trigger() instanceof ScheduleTrigger.FixedRate rate) {
            definition.expect(safePlus(
                    rate.firstExecutionAt(),
                    safeMultiply(
                            rate.period(),
                            execution.executionNumber()
                    )
            ));
        } else if (definition.trigger() instanceof ScheduleTrigger.FixedDelay delay) {
            definition.expect(safePlus(finishedAt, delay.delay()));
        }
    }

    private static Duration safeMultiply(Duration duration, long multiplier) {
        try {
            return duration.multipliedBy(multiplier);
        } catch (ArithmeticException overflow) {
            return Duration.ofSeconds(Long.MAX_VALUE, 999_999_999);
        }
    }

    private static Instant safePlus(Instant instant, Duration duration) {
        try {
            return instant.plus(duration);
        } catch (RuntimeException overflow) {
            return Instant.MAX;
        }
    }
}
