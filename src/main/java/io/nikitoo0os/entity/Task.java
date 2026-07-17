package io.nikitoo0os.entity;

import io.nikitoo0os.entity.enums.TaskState;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class Task {

    private final UUID id;
    private final String name;
    private final Instant createdAt;
    private final Operation parentOperation;

    private final AtomicReference<TaskSnapshot> taskSnapshot;

    public Task(String name, Operation parentOperation) {
        this.id = UUID.randomUUID();
        this.name = validateName(Objects.requireNonNull(name));
        this.parentOperation = Objects.requireNonNull(parentOperation);
        this.createdAt = Instant.now();
        this.taskSnapshot = new AtomicReference<>(new TaskSnapshot(null, null, TaskState.CREATED));
    }

    public void start() {
        TaskSnapshot currentSnapshot = taskSnapshot.get();
        if (currentSnapshot.getState() == TaskState.CREATED) {
            TaskSnapshot newSnapshot = new TaskSnapshot(Instant.now(), null, TaskState.RUNNING);
            boolean changed = taskSnapshot.compareAndSet(currentSnapshot, newSnapshot);
            if(!changed){
                throw new IllegalStateException("The other thread has already started the task");
            }
        } else {
            throw new IllegalStateException("The task cannot switch from state " + currentSnapshot.getState() + " to state running");
        }

    }

    public void complete() {
        finishState(TaskState.COMPLETED);
    }

    public void fail() {
        finishState(TaskState.FAILED);
    }

    private void finishState(TaskState taskState) {
        TaskSnapshot currentSnapshot = taskSnapshot.get();
        if (taskState != null) {
            if (taskState != TaskState.RUNNING) {
                if (currentSnapshot.getState() == TaskState.RUNNING) {
                    TaskSnapshot newSnapshot = new TaskSnapshot(currentSnapshot.getStartedAt(), Instant.now(), taskState);
                    boolean changed = taskSnapshot.compareAndSet(currentSnapshot, newSnapshot);
                    if(!changed){
                        throw new IllegalStateException("The other thread has already finished the task");
                    }
                } else {
                    throw new IllegalStateException("The task cannot switch from state " + currentSnapshot.getState() + " to state " + taskState.name() + ".");
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
            throw new IllegalArgumentException("Name must not be blank");
        } else {
            return name;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Operation getParentOperation() {
        return parentOperation;
    }

    public TaskState getState() {
        return this.taskSnapshot.get().getState();
    }

    public Instant getStartedAt() {
        return taskSnapshot.get().getStartedAt();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return taskSnapshot.get().getFinishedAt();
    }

    public boolean isFinished() {
        TaskState currentSnapshotState = taskSnapshot.get().getState();
        return currentSnapshotState != TaskState.RUNNING && currentSnapshotState != TaskState.CREATED;
    }
}