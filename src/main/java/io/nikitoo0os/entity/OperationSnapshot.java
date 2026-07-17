package io.nikitoo0os.entity;

import io.nikitoo0os.entity.enums.OperationState;

import java.time.Instant;

public final class OperationSnapshot {
    private final Instant finishedAt;
    private final OperationState operationState;

    public OperationSnapshot(Instant finishedAt, OperationState operationState) {
        if (operationState == null) {
            throw new IllegalArgumentException("Operation state must not be null");
        }

        if (operationState == OperationState.RUNNING && finishedAt != null) {
            throw new IllegalStateException("A running operation cannot be completed");
        }

        if (operationState != OperationState.RUNNING && finishedAt == null) {
            throw new IllegalStateException("When the operation is completed, the completion time must not be null.");
        }
        this.finishedAt = finishedAt;
        this.operationState = operationState;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public OperationState getOperationState() {
        return operationState;
    }
}
