package io.nikitoo0os.entity;

import io.nikitoo0os.entity.enums.OperationState;
import io.nikitoo0os.entity.enums.TaskState;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Detector {
    private final Registry registry;

    public Detector(Registry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    public void detect(UUID operationId) {
        Operation operation = registry.findOperation(operationId);
        if (operation.getState() != OperationState.RUNNING) {
            List<Task> tasks = registry.findTasksByOperation(operationId);
            for (Task t : tasks) {
                if (t.getState() == TaskState.RUNNING) {
                    Duration duration = Duration.between(operation.getFinishedAt(), Instant.now());
                    System.out.println("Operation id: " + operationId + "\n " +
                            "Operation name: " + operation.getName() + "\n " +
                            "Operation state: " + operation.getState().name() + "\n " +
                            "Task id: " + t.getId() + "\n " +
                            "Task name: " + t.getName() + "\n " +
                            "Task state: " + t.getState() + "\n " +
                            "Ghost duration: " + duration.toMillis() + " ms");
                }
            }
        }
    }
}
