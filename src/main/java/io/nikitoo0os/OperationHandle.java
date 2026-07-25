package io.nikitoo0os;

import io.nikitoo0os.context.OperationContext;
import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.enums.OperationState;
import io.nikitoo0os.event.GhostWorkEvent;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.event.GhostWorkEventType;

import java.util.Objects;
import java.util.UUID;

public final class OperationHandle {
    private final Operation operation;
    private final GhostWorkEventPublisher events;

    OperationHandle(Operation operation, GhostWorkEventPublisher events) {
        this.operation = Objects.requireNonNull(operation);
        this.events = Objects.requireNonNull(events);
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
        return finish(
                OperationState.COMPLETED,
                GhostWorkEventType.OPERATION_COMPLETED,
                null
        );
    }

    public boolean fail(Throwable failure) {
        return finish(
                OperationState.FAILED,
                GhostWorkEventType.OPERATION_FAILED,
                failure
        );
    }

    public boolean timeout() {
        return finish(
                OperationState.TIMED_OUT,
                GhostWorkEventType.OPERATION_TIMED_OUT,
                null
        );
    }

    public boolean abort(Throwable failure) {
        return finish(
                OperationState.ABORTED,
                GhostWorkEventType.OPERATION_ABORTED,
                failure
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
}
