package io.nikitoo0os.entity;

import io.nikitoo0os.entity.enums.OperationState;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class Operation {

    private final UUID id;
    private final String name;
    private final Instant startedAt;
    private final AtomicReference<OperationSnapshot> stateSnapshot;

    public Operation(String name) {
        this.startedAt = Instant.now();
        this.id = UUID.randomUUID();
        this.name = validateName(Objects.requireNonNull(name));
        stateSnapshot = new AtomicReference<>(new OperationSnapshot(null, OperationState.RUNNING));
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public OperationState getState() {
        return stateSnapshot.get().getOperationState();
    }

    public void complete() {
        finishState(OperationState.COMPLETED);
    }

    public void timeout() {
        finishState(OperationState.TIMED_OUT);
    }

    public void fail() {
        finishState(OperationState.FAILED);
    }

    private void finishState(OperationState operationState) {
        if (operationState != null) {
            if (operationState != OperationState.RUNNING) {
                OperationSnapshot currentSnapshot = stateSnapshot.get();
                if (currentSnapshot.getOperationState() == OperationState.RUNNING) {
                    OperationSnapshot newSnapshot = new OperationSnapshot(Instant.now(), operationState);
                    boolean changed = stateSnapshot.compareAndSet(currentSnapshot, newSnapshot);
                    if (!changed) {
                        throw new IllegalStateException("The other thread has already completed the operation");
                    }
                } else {
                    throw new IllegalStateException("The operation cannot switch from state " + currentSnapshot.getOperationState() + " to state " + operationState.name() + ".");
                }
            } else {
                throw new IllegalArgumentException("RUNNING cannot be used as the final state.");
            }
        } else {
            throw new NullPointerException("Target state must not be null");
        }

    }

    private static String validateName(String name) {
        if (name.trim().isBlank()) {
            throw new IllegalArgumentException("Operation name must not be blank");
        } else {
            return name;
        }
    }

    public boolean isFinished() {
        return this.stateSnapshot.get().getOperationState() != OperationState.RUNNING;
    }

    public Instant getFinishedAt() {
        return this.stateSnapshot.get().getFinishedAt();
    }

}