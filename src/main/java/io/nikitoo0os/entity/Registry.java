package io.nikitoo0os.entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Clock;
import io.nikitoo0os.event.GhostWorkEventPublisher;

public final class Registry {
    private final ConcurrentHashMap<UUID, Operation> operations;
    private final ConcurrentHashMap<UUID, Task> tasks;
    private final ConcurrentHashMap<UUID, Queue<Task>> operationTasks;
    private final Object cleanupLock = new Object();
    private final AtomicReference<CancellationCoordinator> cancellation =
            new AtomicReference<>();
    private final ConcurrentHashMap<UUID, java.util.concurrent.atomic.AtomicInteger>
            operationRetentionLeases = new ConcurrentHashMap<>();

    public Registry() {
        this.operationTasks = new ConcurrentHashMap<>();
        operations = new ConcurrentHashMap<>();
        tasks = new ConcurrentHashMap<>();
    }

    public void registerOperation(Operation operation) {
        if (operation != null) {
            Operation previousOperation =
                    operations.putIfAbsent(operation.getId(), operation);

            if (previousOperation != null) {
                throw new IllegalStateException(
                        "Registry already contains an operation with UUID: "
                                + operation.getId()
                );
            }

        } else {
            throw new NullPointerException("Operation must not be null");
        }
    }

    public void registerTask(Task task) {
        if (task == null) {
            throw new NullPointerException("Task must not be null");
        }

        synchronized (cleanupLock) {
            Operation operation =
                    findOperation(task.getParentOperation().getId());

            if (operation.isFinished()) {
                throw new IllegalStateException(
                        "Cannot register a task for a finished operation"
                );
            }

            Task previousTask = tasks.putIfAbsent(task.getId(), task);
            if (previousTask != null) {
                throw new IllegalStateException(
                        "Registry already contains a task with UUID: "
                                + task.getId()
                );
            }

            UUID operationId = task.getParentOperation().getId();
            Queue<Task> operationTask = operationTasks.computeIfAbsent(
                    operationId,
                    ignored -> new ConcurrentLinkedQueue<>()
            );
            operationTask.add(task);
        }
    }

    public Operation findOperation(UUID uuid) {
        if (uuid != null) {
            Operation operation = operations.getOrDefault(uuid, null);
            if (operation != null) {
                return operation;
            } else {
                throw new NoSuchElementException("Operation with id: " + uuid + " not found");
            }
        } else {
            throw new NullPointerException("UUID operation must not be null");
        }
    }

    public Task findTask(UUID uuid) {
        if (uuid != null) {
            Task task = tasks.getOrDefault(uuid, null);
            if (task != null) {
                return task;
            } else {
                throw new NoSuchElementException("Task with id: " + uuid + " not found");
            }
        } else {
            throw new NullPointerException("UUID task must not be null");
        }
    }

    public List<Task> findTasksByOperation(UUID operationId) {
        if (operationId != null) {
            if (operations.containsKey(operationId)) {
                Queue<Task> operationTaskQueue = operationTasks.get(operationId);

                if (operationTaskQueue == null) {
                    return List.of();
                }

                return List.copyOf(operationTaskQueue);
            } else {
                throw new NoSuchElementException("Operation with id: " + operationId + " not found");
            }

        } else {
            throw new NullPointerException("UUID operation must not be null");
        }
    }

    public List<Operation> findOperations() {
        return List.copyOf(operations.values());
    }

    public void retainOperation(UUID operationId) {
        findOperation(operationId);
        operationRetentionLeases.compute(operationId, (ignored, count) -> {
            if (count == null) {
                return new java.util.concurrent.atomic.AtomicInteger(1);
            }
            count.incrementAndGet();
            return count;
        });
    }

    public void releaseOperation(UUID operationId) {
        Objects.requireNonNull(operationId, "Operation id must not be null");
        operationRetentionLeases.computeIfPresent(
                operationId,
                (ignored, count) -> count.decrementAndGet() <= 0
                        ? null
                        : count
        );
    }

    public CancellationCoordinator cancellationCoordinator(
            Clock clock,
            GhostWorkEventPublisher events
    ) {
        CancellationCoordinator current = cancellation.get();
        if (current != null) {
            return current;
        }
        CancellationCoordinator created =
                new CancellationCoordinator(clock, events);
        return cancellation.compareAndSet(null, created)
                ? created
                : cancellation.get();
    }

    public int cleanupCompletedOperations(
            int maxCompletedOperations,
            java.time.Duration completedOperationTtl,
            java.time.Instant now
    ) {
        if (maxCompletedOperations < 0) {
            throw new IllegalArgumentException(
                    "Maximum completed operations must not be negative"
            );
        }
        Objects.requireNonNull(
                completedOperationTtl,
                "Completed operation TTL must not be null"
        );
        Objects.requireNonNull(now, "Current time must not be null");
        if (completedOperationTtl.isZero()
                || completedOperationTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "Completed operation TTL must be positive"
            );
        }

        synchronized (cleanupLock) {
            List<Operation> removable = operations.values()
                    .stream()
                    .filter(Operation::isFinished)
                    .filter(this::allTasksFinished)
                    .filter(operation ->
                            !operationRetentionLeases.containsKey(operation.getId()))
                    .sorted(Comparator.comparing(Operation::getFinishedAt))
                    .toList();

            Set<UUID> removalIds = new LinkedHashSet<>();
            java.time.Instant cutoff = now.minus(completedOperationTtl);

            removable.stream()
                    .filter(operation ->
                            !operation.getFinishedAt().isAfter(cutoff))
                    .map(Operation::getId)
                    .forEach(removalIds::add);

            List<Operation> retained = removable.stream()
                    .filter(operation -> !removalIds.contains(operation.getId()))
                    .toList();
            int overflow = Math.max(
                    0,
                    retained.size() - maxCompletedOperations
            );
            retained.stream()
                    .limit(overflow)
                    .map(Operation::getId)
                    .forEach(removalIds::add);

            removalIds.forEach(this::removeOperation);
            return removalIds.size();
        }
    }

    private boolean allTasksFinished(Operation operation) {
        Queue<Task> operationTaskQueue =
                operationTasks.get(operation.getId());
        return operationTaskQueue == null
                || operationTaskQueue.stream().allMatch(Task::isFinished);
    }

    private void removeOperation(UUID operationId) {
        Queue<Task> removedTasks = operationTasks.remove(operationId);
        if (removedTasks != null) {
            removedTasks.forEach(task -> tasks.remove(task.getId(), task));
        }
        operations.remove(operationId);
        operationRetentionLeases.remove(operationId);
    }

}
