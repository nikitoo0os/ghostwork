import io.nikitoo0os.Detector;
import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DetectorTest {

    private static final Instant NOW =
            Instant.parse("2026-01-01T10:00:00Z");

    private static final Clock FIXED_CLOCK =
            Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void finishedOperationWithRunningTaskShouldBeDetectedAsGhost() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        Task task = new Task("TestTask", operation);

        registry.registerOperation(operation);
        registry.registerTask(task);

        task.submit();
        task.start(NOW.minusSeconds(30));
        operation.complete();

        Detector detector = new Detector(registry, FIXED_CLOCK);

        List<Task> result =
                detector.detectGhostTasks(operation.getId());

        assertEquals(1, result.size());
        assertSame(task, result.getFirst());
    }

    @Test
    void runningOperationShouldNotHaveGhostTasks() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        Task task = new Task("TestTask", operation);

        registry.registerOperation(operation);
        registry.registerTask(task);

        task.submit();
        task.start(NOW.minusSeconds(30));

        Detector detector = new Detector(registry, FIXED_CLOCK);

        List<Task> result =
                detector.detectGhostTasks(operation.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void finishedOperationWithCompletedTaskShouldNotHaveGhostTasks() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        Task task = new Task("TestTask", operation);

        registry.registerOperation(operation);
        registry.registerTask(task);

        Instant startedAt = NOW.minusSeconds(30);

        task.submit();
        task.start(startedAt);
        task.complete(startedAt.plusSeconds(10));
        operation.complete();

        Detector detector = new Detector(registry, FIXED_CLOCK);

        List<Task> result =
                detector.detectGhostTasks(operation.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void taskRunningLongerThanThresholdShouldBeDetectedAsStuck() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        Task task = new Task("TestTask", operation);

        registry.registerOperation(operation);
        registry.registerTask(task);

        task.submit();
        task.start(NOW.minusSeconds(61));

        Detector detector = new Detector(registry, FIXED_CLOCK);

        List<Task> result = detector.detectStuckTasks(
                operation.getId(),
                Duration.ofSeconds(60)
        );

        assertEquals(1, result.size());
        assertSame(task, result.getFirst());
    }

    @Test
    void taskRunningExactlyThresholdShouldNotBeDetectedAsStuck() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        Task task = new Task("TestTask", operation);

        registry.registerOperation(operation);
        registry.registerTask(task);

        task.submit();
        task.start(NOW.minusSeconds(60));

        Detector detector = new Detector(registry, FIXED_CLOCK);

        List<Task> result = detector.detectStuckTasks(
                operation.getId(),
                Duration.ofSeconds(60)
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void taskRunningLessThanThresholdShouldNotBeDetectedAsStuck() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        Task task = new Task("TestTask", operation);

        registry.registerOperation(operation);
        registry.registerTask(task);

        task.submit();
        task.start(NOW.minusSeconds(59));

        Detector detector = new Detector(registry, FIXED_CLOCK);

        List<Task> result = detector.detectStuckTasks(
                operation.getId(),
                Duration.ofSeconds(60)
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void completedTaskShouldNotBeDetectedAsStuck() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        Task task = new Task("TestTask", operation);

        registry.registerOperation(operation);
        registry.registerTask(task);

        Instant startedAt = NOW.minusSeconds(120);

        task.submit();
        task.start(startedAt);
        task.complete(NOW.minusSeconds(30));

        Detector detector = new Detector(registry, FIXED_CLOCK);

        List<Task> result = detector.detectStuckTasks(
                operation.getId(),
                Duration.ofSeconds(60)
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void failedTaskShouldNotBeDetectedAsStuck() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        Task task = new Task("TestTask", operation);

        registry.registerOperation(operation);
        registry.registerTask(task);

        Instant startedAt = NOW.minusSeconds(120);

        task.submit();
        task.start(startedAt);
        task.fail(NOW.minusSeconds(30));

        Detector detector = new Detector(registry, FIXED_CLOCK);

        List<Task> result = detector.detectStuckTasks(
                operation.getId(),
                Duration.ofSeconds(60)
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void createdTaskShouldNotBeDetectedAsStuck() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        Task task = new Task("TestTask", operation);

        registry.registerOperation(operation);
        registry.registerTask(task);

        Detector detector = new Detector(registry, FIXED_CLOCK);

        List<Task> result = detector.detectStuckTasks(
                operation.getId(),
                Duration.ofSeconds(60)
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void zeroThresholdShouldThrow() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");

        registry.registerOperation(operation);

        Detector detector = new Detector(registry, FIXED_CLOCK);

        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detectStuckTasks(
                        operation.getId(),
                        Duration.ZERO
                )
        );
    }

    @Test
    void negativeThresholdShouldThrow() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");

        registry.registerOperation(operation);

        Detector detector = new Detector(registry, FIXED_CLOCK);

        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detectStuckTasks(
                        operation.getId(),
                        Duration.ofSeconds(-1)
                )
        );
    }

    @Test
    void nullThresholdShouldThrow() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");

        registry.registerOperation(operation);

        Detector detector = new Detector(registry, FIXED_CLOCK);

        assertThrows(
                NullPointerException.class,
                () -> detector.detectStuckTasks(
                        operation.getId(),
                        null
                )
        );
    }

    @Test
    void nullOperationIdShouldThrowWhenDetectingGhostTasks() {
        Registry registry = new Registry();
        Detector detector = new Detector(registry, FIXED_CLOCK);

        assertThrows(
                NullPointerException.class,
                () -> detector.detectGhostTasks(null)
        );
    }

    @Test
    void nullOperationIdShouldThrowWhenDetectingStuckTasks() {
        Registry registry = new Registry();
        Detector detector = new Detector(registry, FIXED_CLOCK);

        assertThrows(
                NullPointerException.class,
                () -> detector.detectStuckTasks(
                        null,
                        Duration.ofSeconds(60)
                )
        );
    }

    @Test
    void unknownOperationShouldThrowWhenDetectingGhostTasks() {
        Registry registry = new Registry();
        Detector detector = new Detector(registry, FIXED_CLOCK);

        UUID unknownOperationId = UUID.randomUUID();

        assertThrows(
                NoSuchElementException.class,
                () -> detector.detectGhostTasks(unknownOperationId)
        );
    }

    @Test
    void unknownOperationShouldThrowWhenDetectingStuckTasks() {
        Registry registry = new Registry();
        Detector detector = new Detector(registry, FIXED_CLOCK);

        UUID unknownOperationId = UUID.randomUUID();

        assertThrows(
                NoSuchElementException.class,
                () -> detector.detectStuckTasks(
                        unknownOperationId,
                        Duration.ofSeconds(60)
                )
        );
    }

    @Test
    void detectorShouldRejectNullRegistry() {
        assertThrows(
                NullPointerException.class,
                () -> new Detector(null, FIXED_CLOCK)
        );
    }

    @Test
    void detectorShouldRejectNullClock() {
        Registry registry = new Registry();

        assertThrows(
                NullPointerException.class,
                () -> new Detector(registry, null)
        );
    }

    @Test
    void submittedTaskShouldBeReportedAsOutlivingFinishedOperation() {
        Registry registry = new Registry();
        Operation operation = new Operation("HTTP request");
        Task task = new Task("Queued notification", operation);
        registry.registerOperation(operation);
        registry.registerTask(task);
        task.submit(NOW.minusSeconds(1));
        operation.complete();

        Detector detector = new Detector(registry, FIXED_CLOCK);
        assertEquals(
                List.of(task),
                detector.detectOutlivedTasks(operation.getId())
        );
        assertTrue(detector.detectGhostTasks(operation.getId()).isEmpty());
    }

    @Test
    void submittedTaskWaitingLongerThanThresholdShouldBeQueuedStuck() {
        Registry registry = new Registry();
        Operation operation = new Operation("Import");
        Task task = new Task("LoadCustomers", operation);
        registry.registerOperation(operation);
        registry.registerTask(task);
        task.submit(NOW.minusSeconds(61));

        List<Task> result = new Detector(registry, FIXED_CLOCK)
                .detectStuckQueuedTasks(
                        operation.getId(),
                        Duration.ofSeconds(60)
                );

        assertEquals(List.of(task), result);
    }

    @Test
    void submittedTaskAtThresholdBoundaryShouldNotBeQueuedStuck() {
        Registry registry = new Registry();
        Operation operation = new Operation("Import");
        Task task = new Task("LoadCustomers", operation);
        registry.registerOperation(operation);
        registry.registerTask(task);
        task.submit(NOW.minusSeconds(60));

        assertTrue(new Detector(registry, FIXED_CLOCK)
                .detectStuckQueuedTasks(
                        operation.getId(),
                        Duration.ofSeconds(60)
                )
                .isEmpty());
    }

    @Test
    void runningAndTerminalTasksShouldNotBeQueuedStuck() {
        Registry registry = new Registry();
        Operation operation = new Operation("Import");
        registry.registerOperation(operation);
        Task running = new Task("Running", operation);
        Task completed = new Task("Completed", operation);
        Task cancelled = new Task("Cancelled", operation);
        Task rejected = new Task("Rejected", operation);
        List.of(running, completed, cancelled, rejected)
                .forEach(registry::registerTask);

        running.submit(NOW.minusSeconds(120));
        running.start(NOW.minusSeconds(90));
        completed.submit(NOW.minusSeconds(120));
        completed.start(NOW.minusSeconds(90));
        completed.complete(NOW.minusSeconds(30));
        cancelled.submit(NOW.minusSeconds(120));
        cancelled.cancel(NOW.minusSeconds(30));
        rejected.reject(NOW.minusSeconds(30));

        assertTrue(new Detector(registry, FIXED_CLOCK)
                .detectStuckQueuedTasks(
                        operation.getId(),
                        Duration.ofSeconds(60)
                )
                .isEmpty());
    }

    @Test
    void queuedDetectionShouldValidateOperationAndThreshold() {
        Registry registry = new Registry();
        Operation operation = new Operation("Import");
        registry.registerOperation(operation);
        Detector detector = new Detector(registry, FIXED_CLOCK);

        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detectStuckQueuedTasks(
                        operation.getId(),
                        Duration.ZERO
                )
        );
        assertThrows(
                NoSuchElementException.class,
                () -> detector.detectStuckQueuedTasks(
                        UUID.randomUUID(),
                        Duration.ofSeconds(1)
                )
        );
    }
}
