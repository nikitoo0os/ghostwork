package io.nikitoo0os.entity;

import io.nikitoo0os.entity.enums.TaskState;

import java.time.Instant;

public final class TaskSnapshot {
    private final Instant submittedAt;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final TaskState state;

    public TaskSnapshot(Instant startedAt, Instant finishedAt, TaskState state) {
        this(null, startedAt, finishedAt, state);
    }

    public TaskSnapshot(
            Instant submittedAt,
            Instant startedAt,
            Instant finishedAt,
            TaskState state
    ) {
        if (state != null) {
            if (state == TaskState.CREATED) {
                if (submittedAt != null || startedAt != null || finishedAt != null) {
                    throw new IllegalStateException("Created task should not have lifecycle timestamps.");
                }
            }

            if (state == TaskState.SUBMITTED) {
                if (startedAt != null || finishedAt != null) {
                    throw new IllegalStateException("Submitted task must not have start or finish time.");
                }
            }

            if (state == TaskState.RUNNING) {
                if (startedAt == null || finishedAt != null) {
                    throw new IllegalStateException("Task with the Running state must have an start time but not an end time.");
                }
            }

            if (state == TaskState.COMPLETED || state == TaskState.FAILED) {
                if (startedAt == null || finishedAt == null) {
                    throw new IllegalStateException("A finished task must have both start and finish times.");
                }
            }

            if(state == TaskState.REJECTED){
                if (startedAt != null || finishedAt == null) {
                    throw new IllegalStateException(
                            "Rejected task must have finish time but no start time."
                    );
                }
            }

            if(state == TaskState.CANCELLED){
                if(finishedAt == null){
                    throw new IllegalStateException(
                            "Cancelled task must have finish time."
                    );
                }
            }

            this.submittedAt = submittedAt;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.state = state;
        } else {
            throw new NullPointerException("Task state must not be null");
        }
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public TaskState getState() {
        return state;
    }
}
