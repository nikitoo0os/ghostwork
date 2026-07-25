package io.nikitoo0os;

import io.nikitoo0os.context.OperationContext;
import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.enums.OperationState;
import io.nikitoo0os.event.GhostWorkEvent;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.event.GhostWorkEventType;
import io.nikitoo0os.entity.CancellationCoordinator;
import io.nikitoo0os.entity.Registry;

import java.util.Objects;
import java.util.UUID;

public final class OperationHandle {
    private final Operation operation;
    private final GhostWorkEventPublisher events;
    private final CancellationCoordinator cancellation;
    private final CancellationPolicy policy;
    private final Registry registry;

    OperationHandle(Operation operation, GhostWorkEventPublisher events) {
        this(
                operation,
                events,
                new CancellationCoordinator(
                        java.time.Clock.systemUTC(),
                        events
                ),
                CancellationPolicy.conservativeDefaults(),
                null
        );
    }

    OperationHandle(
            Operation operation,
            GhostWorkEventPublisher events,
            CancellationCoordinator cancellation,
            CancellationPolicy policy,
            Registry registry
    ) {
        this.operation = Objects.requireNonNull(operation);
        this.events = Objects.requireNonNull(events);
        this.cancellation = Objects.requireNonNull(cancellation);
        this.policy = Objects.requireNonNull(policy);
        this.registry = registry;
    }

    public UUID id() {
        return operation.getId();
    }

    public OperationView view() {
        return OperationView.from(operation);
    }

    public OperationScope openScope() {
        OperationContext.Scope scope = OperationContext.open(operation);
        return scope::close;
    }

    public void updateMetadata(OperationMetadata metadata) {
        operation.setMetadata(metadata);
    }

    public boolean complete() {
        boolean changed = finish(
                OperationState.COMPLETED,
                GhostWorkEventType.OPERATION_COMPLETED,
                null
        );
        if (changed) {
            propagate(CancellationTrigger.OPERATION_COMPLETE);
        }
        return changed;
    }

    public boolean fail(Throwable failure) {
        boolean changed = finish(
                OperationState.FAILED,
                GhostWorkEventType.OPERATION_FAILED,
                failure
        );
        if (changed) {
            propagate(CancellationTrigger.OPERATION_FAILURE);
        }
        return changed;
    }

    public boolean timeout() {
        boolean changed = finish(
                OperationState.TIMED_OUT,
                GhostWorkEventType.OPERATION_TIMED_OUT,
                null
        );
        if (changed) {
            propagate(CancellationTrigger.OPERATION_TIMEOUT);
        }
        return changed;
    }

    public boolean abort(Throwable failure) {
        boolean changed = finish(
                OperationState.ABORTED,
                GhostWorkEventType.OPERATION_ABORTED,
                failure
        );
        if (changed) {
            propagate(CancellationTrigger.CLIENT_ABORT);
        }
        return changed;
    }

    public CancellationResult cancel() {
        return cancel(CancellationCause.USER_REQUEST);
    }

    public CancellationResult cancel(CancellationCause cause) {
        CancellationDecision decision = policy.decide(
                view(),
                CancellationTrigger.OPERATION_CANCEL,
                activeTasks()
        );
        return cancellation.cancelOperation(
                operation,
                options(cause, decision.policy()),
                true
        );
    }

    private boolean finish(
            OperationState state,
            GhostWorkEventType eventType,
            Throwable failure
    ) {
        if (!operation.tryFinish(state)) {
            return false;
        }
        events.publish(new GhostWorkEvent(
                eventType,
                OperationView.from(operation),
                null,
                failure
        ));
        return true;
    }

    private void propagate(CancellationTrigger trigger) {
        CancellationDecision decision = policy.decide(
                view(),
                trigger,
                activeTasks()
        );
        if (decision.policy() == ChildCancellationPolicy.NONE) {
            return;
        }
        cancellation.cancelOperation(
                operation,
                options(cause(trigger), decision.policy()),
                false
        );
    }

    private static CancellationOptions options(
            CancellationCause cause,
            ChildCancellationPolicy policy
    ) {
        return new CancellationOptions(
                cause,
                policy.cancelQueued(),
                policy.interruptRunning()
        );
    }

    private static CancellationCause cause(CancellationTrigger trigger) {
        return switch (trigger) {
            case OPERATION_TIMEOUT -> CancellationCause.OPERATION_TIMED_OUT;
            case CLIENT_ABORT -> CancellationCause.CLIENT_ABORTED;
            case OPERATION_FAILURE -> CancellationCause.OPERATION_FAILED;
            case OPERATION_COMPLETE -> CancellationCause.UNKNOWN;
            case APPLICATION_SHUTDOWN -> CancellationCause.APPLICATION_SHUTDOWN;
            case PARENT_TASK_CANCEL -> CancellationCause.PARENT_TASK_CANCELLED;
            case OPERATION_CANCEL -> CancellationCause.OPERATION_CANCELLED;
        };
    }

    private java.util.List<TaskView> activeTasks() {
        if (registry == null) {
            return java.util.List.of();
        }
        return registry.findTasksByOperation(id()).stream()
                .filter(task -> !task.isFinished())
                .map(TaskView::from)
                .toList();
    }
}
