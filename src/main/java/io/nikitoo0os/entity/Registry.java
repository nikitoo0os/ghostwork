package io.nikitoo0os.entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class Registry {
    private final ConcurrentHashMap<UUID, Operation> operations;
    private final ConcurrentHashMap<UUID, Task> tasks;
    private final ConcurrentHashMap<UUID, Queue<Task>> operationTasks;

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
        if (task != null) {
            findOperation(task.getParentOperation().getId());
            Task previousTask = tasks.putIfAbsent(task.getId(), task);
            if (previousTask != null) {
                throw new IllegalStateException("Registry already contains a task with UUID: " + task.getId());
            }

            UUID operationId = task.getParentOperation().getId();

            Queue<Task> operationTask =
                    operationTasks.computeIfAbsent(operationId, _ -> new ConcurrentLinkedQueue<>());

            operationTask.add(task);
        } else {
            throw new NullPointerException("Task must not be null");
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
}
