package io.nikitoo0os.entity;

import io.nikitoo0os.entity.enums.TaskState;

import java.time.Duration;
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

    public void start(Instant startedAt) {
        Objects.requireNonNull(
                startedAt,
                "Started time must not be null"
        );

        TaskSnapshot currentSnapshot = taskSnapshot.get();

        if (currentSnapshot.getState() != TaskState.SUBMITTED) {
            throw new IllegalStateException(
                    "The task cannot switch from state " +
                            currentSnapshot.getState() +
                            " to state " +
                            TaskState.RUNNING
            );
        }

        TaskSnapshot newSnapshot = new TaskSnapshot(
                startedAt,
                null,
                TaskState.RUNNING
        );

        boolean changed = taskSnapshot.compareAndSet(
                currentSnapshot,
                newSnapshot
        );

        if (!changed) {
            throw new IllegalStateException(
                    "The other thread has already started the task"
            );
        }
    }

    public void submit() {
        TaskSnapshot currentSnapshot = taskSnapshot.get();

        if (currentSnapshot.getState() != TaskState.CREATED) {
            throw new IllegalStateException(
                    "The task cannot switch from state "
                            + currentSnapshot.getState()
                            + " to state "
                            + TaskState.SUBMITTED
            );
        }

        boolean changed = taskSnapshot.compareAndSet(
                currentSnapshot,
                new TaskSnapshot(null, null, TaskState.SUBMITTED)
        );

        if (!changed) {
            throw new IllegalStateException(
                    "The other thread has already changed the task state"
            );
        }
    }

    public void complete(Instant finishedAt) {
        finishState(finishedAt, TaskState.COMPLETED);
    }

    public void fail(Instant finishedAt) {
        finishState(finishedAt, TaskState.FAILED);
    }

    public void cancel(Instant finishedAt) {
        Objects.requireNonNull(
                finishedAt,
                "Finished time must not be null"
        );

        TaskSnapshot currentSnapshot = taskSnapshot.get();

        if (currentSnapshot.getState() != TaskState.CREATED &&
                currentSnapshot.getState() != TaskState.SUBMITTED &&
                currentSnapshot.getState() != TaskState.RUNNING) {
            throw new IllegalStateException(
                    "The task cannot switch from state " +
                            currentSnapshot.getState() +
                            " to state " +
                            TaskState.CANCELLED
            );
        }

        if (currentSnapshot.getStartedAt() != null &&
                finishedAt.isBefore(currentSnapshot.getStartedAt())) {
            throw new IllegalArgumentException(
                    "Finished time cannot be before task start time"
            );
        }

        TaskSnapshot newSnapshot = new TaskSnapshot(
                currentSnapshot.getStartedAt(),
                finishedAt,
                TaskState.CANCELLED
        );

        boolean changed = taskSnapshot.compareAndSet(
                currentSnapshot,
                newSnapshot
        );

        if (!changed) {
            throw new IllegalStateException(
                    "The other thread has already changed the task state"
            );
        }
    }

    public void reject(Instant finishedAt) {
        Objects.requireNonNull(
                finishedAt,
                "Finished time must not be null"
        );

        TaskSnapshot currentSnapshot = taskSnapshot.get();

        if (currentSnapshot.getState() != TaskState.CREATED &&
                currentSnapshot.getState() != TaskState.SUBMITTED) {
            throw new IllegalStateException(
                    "The task cannot switch from state " +
                            currentSnapshot.getState() +
                            " to state " +
                            TaskState.REJECTED
            );
        }

        TaskSnapshot newSnapshot = new TaskSnapshot(
                null,
                finishedAt,
                TaskState.REJECTED
        );

        boolean changed = taskSnapshot.compareAndSet(
                currentSnapshot,
                newSnapshot
        );

        if (!changed) {
            throw new IllegalStateException(
                    "The other thread has already started or rejected the task"
            );
        }
    }

    private void finishState(
            Instant finishedAt,
            TaskState targetState
    ) {
        Objects.requireNonNull(
                finishedAt,
                "Finished time must not be null"
        );
        Objects.requireNonNull(
                targetState,
                "Target state must not be null"
        );

        if (targetState == TaskState.RUNNING ||
                targetState == TaskState.CREATED) {
            throw new IllegalArgumentException(
                    "Target state must be final"
            );
        }

        TaskSnapshot currentSnapshot = taskSnapshot.get();

        if (currentSnapshot.getState() != TaskState.RUNNING) {
            throw new IllegalStateException(
                    "The task cannot switch from state " +
                            currentSnapshot.getState() +
                            " to state " +
                            targetState
            );
        }

        if (finishedAt.isBefore(currentSnapshot.getStartedAt())) {
            throw new IllegalArgumentException(
                    "Finished time cannot be before task start time"
            );
        }

        TaskSnapshot newSnapshot = new TaskSnapshot(
                currentSnapshot.getStartedAt(),
                finishedAt,
                targetState
        );

        boolean changed = taskSnapshot.compareAndSet(
                currentSnapshot,
                newSnapshot
        );

        if (!changed) {
            throw new IllegalStateException(
                    "The other thread has already finished the task"
            );
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
        TaskState state = taskSnapshot.get().getState();
        return state == TaskState.COMPLETED ||
                state == TaskState.FAILED ||
                state == TaskState.CANCELLED ||
                state == TaskState.REJECTED;
    }

    public boolean isRunningLongerThan(
            Duration threshold,
            Instant targetTime
    ) {
        Objects.requireNonNull(threshold, "Threshold must not be null");
        Objects.requireNonNull(targetTime, "Target time must not be null");

        if (threshold.isZero() || threshold.isNegative()) {
            throw new IllegalArgumentException(
                    "Threshold must be positive"
            );
        }

        TaskSnapshot snapshot = taskSnapshot.get();

        if (snapshot.getState() != TaskState.RUNNING) {
            return false;
        }

        if (targetTime.isBefore(snapshot.getStartedAt())) {
            throw new IllegalArgumentException(
                    "Target time cannot be before task start time"
            );
        }

        Duration runningDuration = Duration.between(
                snapshot.getStartedAt(),
                targetTime
        );

        return runningDuration.compareTo(threshold) > 0;
    }
}
