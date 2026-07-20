import io.nikitoo0os.TrackingExecutorService;
import io.nikitoo0os.context.OperationContext;
import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.entity.enums.OperationState;
import io.nikitoo0os.entity.enums.TaskState;
import io.nikitoo0os.event.GhostWorkEvent;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.event.GhostWorkEventType;
import io.nikitoo0os.factory.TrackingCallableFactory;
import io.nikitoo0os.factory.TrackingRunnableFactory;
import io.nikitoo0os.runner.OperationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class OperationRunnerTest {

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-01-01T10:00:00Z"),
            ZoneOffset.UTC
    );

    private final Registry registry = new Registry();

    private final ExecutorService executor =
            Executors.newFixedThreadPool(2);

    private final GhostWorkEventPublisher eventPublisher =
            new GhostWorkEventPublisher();

    private final TrackingExecutorService trackingExecutor =
            new TrackingExecutorService(
                    executor,
                    new TrackingRunnableFactory(registry, clock),
                    new TrackingCallableFactory(registry, clock),
                    clock,
                    new GhostWorkEventPublisher()
            );

    private final OperationRunner operationRunner =
            new OperationRunner(registry, eventPublisher);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        OperationContext.clear();
    }

    @Test
    void callShouldTrackSubmittedTasksUnderCreatedOperation()
            throws Exception {
        operationRunner.call(
                "ImportUsers",
                () -> {
                    trackingExecutor.submit(
                            "ReadFile",
                            () -> {
                            }
                    ).get(1, TimeUnit.SECONDS);

                    trackingExecutor.submit(
                            "ValidateRows",
                            () -> {
                            }
                    ).get(1, TimeUnit.SECONDS);

                    return null;
                }
        );

        Operation operation = findSingleOperationByName("ImportUsers");
        List<Task> tasks =
                registry.findTasksByOperation(operation.getId());

        assertEquals(OperationState.COMPLETED, operation.getState());
        assertEquals(2, tasks.size());

        assertTrue(tasks.stream().anyMatch(task ->
                task.getName().equals("ReadFile")
                        && task.getState() == TaskState.COMPLETED
        ));

        assertTrue(tasks.stream().anyMatch(task ->
                task.getName().equals("ValidateRows")
                        && task.getState() == TaskState.COMPLETED
        ));
    }

    private Operation findSingleOperationByName(String name) {
        return registry.findOperations()
                .stream()
                .filter(operation -> operation.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
    @Test
    void callShouldFailOperationAndRethrowOriginalException() {
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> operationRunner.call(
                        "ImportUsers",
                        () -> {
                            throw new RuntimeException("Import failed");
                        }
                )
        );

        assertEquals("Import failed", exception.getMessage());

        Operation operation = findSingleOperationByName("ImportUsers");

        assertEquals(OperationState.FAILED, operation.getState());
        assertNotNull(operation.getFinishedAt());
        assertTrue(OperationContext.current().isEmpty());
    }
    @Test
    void callShouldPublishOperationCompletedEvent()
            throws Exception {
        AtomicReference<GhostWorkEvent> receivedEvent =
                new AtomicReference<>();

        eventPublisher.addListener(receivedEvent::set);

        operationRunner.call(
                "ImportUsers",
                () -> "done"
        );

        GhostWorkEvent event = receivedEvent.get();

        assertNotNull(event);
        assertEquals(GhostWorkEventType.OPERATION_COMPLETED, event.type());
        assertEquals("ImportUsers", event.operation().name());
        assertEquals(OperationState.COMPLETED, event.operation().state());
        assertNull(event.task());
        assertNull(event.failure());
    }
    @Test
    void callShouldPublishOperationFailedEvent() {
        AtomicReference<GhostWorkEvent> receivedEvent =
                new AtomicReference<>();

        eventPublisher.addListener(receivedEvent::set);

        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> operationRunner.call(
                        "ImportUsers",
                        () -> {
                            throw new RuntimeException("Import failed");
                        }
                )
        );

        GhostWorkEvent event = receivedEvent.get();

        assertNotNull(event);
        assertEquals(GhostWorkEventType.OPERATION_FAILED, event.type());
        assertEquals("ImportUsers", event.operation().name());
        assertEquals(OperationState.FAILED, event.operation().state());
        assertNull(event.task());
        assertSame(failure, event.failure());
    }

    @Test
    void runShouldPublishOperationCompletedEvent() {
        AtomicReference<GhostWorkEvent> receivedEvent =
                new AtomicReference<>();

        eventPublisher.addListener(receivedEvent::set);

        operationRunner.run(
                "Cleanup",
                () -> {
                }
        );

        GhostWorkEvent event = receivedEvent.get();

        assertNotNull(event);
        assertEquals(GhostWorkEventType.OPERATION_COMPLETED, event.type());
        assertEquals("Cleanup", event.operation().name());
        assertEquals(OperationState.COMPLETED, event.operation().state());
        assertNull(event.task());
        assertNull(event.failure());
    }

    @Test
    void runShouldPublishOperationFailedEvent() {
        AtomicReference<GhostWorkEvent> receivedEvent =
                new AtomicReference<>();

        eventPublisher.addListener(receivedEvent::set);

        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> operationRunner.run(
                        "Cleanup",
                        () -> {
                            throw new RuntimeException("Cleanup failed");
                        }
                )
        );

        GhostWorkEvent event = receivedEvent.get();

        assertNotNull(event);
        assertEquals(GhostWorkEventType.OPERATION_FAILED, event.type());
        assertEquals("Cleanup", event.operation().name());
        assertEquals(OperationState.FAILED, event.operation().state());
        assertNull(event.task());
        assertSame(failure, event.failure());
    }
}