package io.nikitoo0os;

import java.util.List;

public record OperationDetails(
        OperationView operation,
        OperationMetadata metadata,
        List<TaskDiagnostics> tasks,
        List<TaskDiagnostics> ghostTasks,
        List<TimelineEntry> timeline,
        OperationCancellationView cancellation
) {
    public OperationDetails(
            OperationView operation,
            OperationMetadata metadata,
            List<TaskDiagnostics> tasks,
            List<TaskDiagnostics> ghostTasks,
            List<TimelineEntry> timeline
    ) {
        this(
                operation,
                metadata,
                tasks,
                ghostTasks,
                timeline,
                OperationCancellationView.none()
        );
    }

    public OperationDetails {
        tasks = List.copyOf(tasks);
        ghostTasks = List.copyOf(ghostTasks);
        timeline = List.copyOf(timeline);
    }
}
