package io.nikitoo0os;

import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.entity.enums.TaskState;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Detector {
    private final Registry registry;
    private final Clock clock;

    public Detector(Registry registry, Clock clock) {
        this.registry = Objects.requireNonNull(registry);
        this.clock = Objects.requireNonNull(clock);
    }

    public Detector(Registry registry) {
        this(registry, Clock.systemUTC());
    }

    public List<Task> detectGhostTasks(UUID operationId) {
        Objects.requireNonNull(operationId, "Operation id must not be null");
        Operation operation = registry.findOperation(operationId);
        List<Task> result = new ArrayList<>();
        if (operation.isFinished()) {
            List<Task> tasks = registry.findTasksByOperation(operationId);
            for (Task t : tasks) {
                if (t.getState() == TaskState.RUNNING) {
                    result.add(t);
                }
            }
        }
        return result;
    }

    public List<Task> detectStuckTasks(
            UUID operationId,
            Duration threshold
    ) {
        Objects.requireNonNull(operationId, "Operation id must not be null");
        Objects.requireNonNull(threshold, "Threshold must not be null");

        if (threshold.isZero() || threshold.isNegative()) {
            throw new IllegalArgumentException(
                    "Threshold must be positive"
            );
        }

        registry.findOperation(operationId);

        List<Task> tasks =
                registry.findTasksByOperation(operationId);

        List<Task> result = new ArrayList<>();

        Instant targetTime = Instant.now(clock);

        for (Task task : tasks) {
            if (task.isRunningLongerThan(threshold, targetTime)) {
                result.add(task);
            }
        }

        return result;
    }
}
