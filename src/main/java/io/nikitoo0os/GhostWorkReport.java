package io.nikitoo0os;

import java.util.List;
import java.util.Objects;

public record GhostWorkReport(
        List<OperationView> operations,
        List<TaskView> tasks,
        List<TaskView> ghostTasks,
        List<TaskView> stuckTasks
) {
    public GhostWorkReport {
        operations = List.copyOf(
                Objects.requireNonNull(operations, "Operations must not be null")
        );
        tasks = List.copyOf(
                Objects.requireNonNull(tasks, "Tasks must not be null")
        );
        ghostTasks = List.copyOf(
                Objects.requireNonNull(ghostTasks, "Ghost tasks must not be null")
        );
        stuckTasks = List.copyOf(
                Objects.requireNonNull(stuckTasks, "Stuck tasks must not be null")
        );
    }
}