import io.nikitoo0os.GhostWork;
import io.nikitoo0os.RetentionPolicy;
import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class RetentionPolicyTest {

    @Test
    void policyShouldRejectInvalidValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetentionPolicy(
                        -1,
                        Duration.ofHours(1),
                        Duration.ofMinutes(1)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetentionPolicy(
                        1,
                        Duration.ZERO,
                        Duration.ofMinutes(1)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetentionPolicy(
                        1,
                        Duration.ofHours(1),
                        Duration.ZERO
                )
        );
    }

    @Test
    void cleanupShouldRemoveExpiredOperationAndItsTasks() {
        Registry registry = new Registry();
        Operation operation = new Operation("expired");
        Task task = new Task("completed", operation);
        registry.registerOperation(operation);
        registry.registerTask(task);
        task.submit();
        task.start(Instant.now());
        task.complete(Instant.now());
        operation.complete();

        int removed = registry.cleanupCompletedOperations(
                10,
                Duration.ofHours(1),
                Instant.now().plus(Duration.ofHours(2))
        );

        assertEquals(1, removed);
        assertTrue(registry.findOperations().isEmpty());
        assertThrows(
                NoSuchElementException.class,
                () -> registry.findTask(task.getId())
        );
    }

    @Test
    void cleanupShouldEnforceMaximumCompletedOperations() {
        Registry registry = new Registry();
        Operation first = completedOperation(registry, "first");
        Operation second = completedOperation(registry, "second");

        int removed = registry.cleanupCompletedOperations(
                1,
                Duration.ofDays(1),
                Instant.now()
        );

        assertEquals(1, removed);
        assertEquals(1, registry.findOperations().size());
        assertTrue(
                registry.findOperations().getFirst().getId().equals(first.getId())
                        || registry.findOperations().getFirst().getId()
                        .equals(second.getId())
        );
    }

    @Test
    void cleanupShouldKeepFinishedOperationWithActiveTask() {
        Registry registry = new Registry();
        Operation operation = new Operation("active-task");
        Task task = new Task("running", operation);
        registry.registerOperation(operation);
        registry.registerTask(task);
        task.submit();
        task.start(Instant.now());
        operation.complete();

        int removed = registry.cleanupCompletedOperations(
                0,
                Duration.ofSeconds(1),
                Instant.now().plusSeconds(2)
        );

        assertEquals(0, removed);
        assertSame(operation, registry.findOperation(operation.getId()));
        assertSame(task, registry.findTask(task.getId()));
    }

    @Test
    void ghostWorkShouldExposeManualCleanup() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            GhostWork ghostWork = GhostWork.create(
                    executor,
                    new RetentionPolicy(
                            0,
                            Duration.ofDays(1),
                            Duration.ofMinutes(5)
                    )
            );
            ghostWork.run("completed", () -> {
            });

            assertEquals(1, ghostWork.cleanup());
            assertTrue(ghostWork.operations().isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }

    private static Operation completedOperation(
            Registry registry,
            String name
    ) {
        Operation operation = new Operation(name);
        registry.registerOperation(operation);
        operation.complete();
        return operation;
    }
}
