package io.nikitoo0os.entity;

import io.nikitoo0os.entity.enums.TaskState;

import java.time.Instant;

public final class TaskSnapshot {
    private final Instant startedAt;
    private final Instant finishedAt;
    private final TaskState state;

    public TaskSnapshot(Instant startedAt, Instant finishedAt, TaskState state) {
        if (state != null) {
            if (state == TaskState.CREATED) {
                if (startedAt != null || finishedAt != null) {
                    throw new IllegalStateException("Task with the " + state.name() + " state should not have a start and end time.");
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


            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.state = state;
        } else {
            throw new NullPointerException("Task state must not be null");
        }
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
