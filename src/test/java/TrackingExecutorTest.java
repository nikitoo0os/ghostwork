import io.nikitoo0os.TrackingExecutorService;
import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.entity.enums.TaskState;
import io.nikitoo0os.factory.TrackingCallableFactory;
import io.nikitoo0os.factory.TrackingRunnableFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class TrackingExecutorTest {

    private Clock clock;
    private Registry registry;
    private Operation operation;
    private ExecutorService executor;
    private TrackingExecutorService trackingExecutor;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
                Instant.parse("2026-01-01T10:00:00Z"),
                ZoneOffset.UTC
        );

        registry = new Registry();

        operation = new Operation("TestOperation");
        registry.registerOperation(operation);

        executor = Executors.newSingleThreadExecutor();

        trackingExecutor = new TrackingExecutorService(
                executor,
                new TrackingRunnableFactory(registry, clock),
                new TrackingCallableFactory(registry, clock)
        );
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void submitShouldTrackAndCompleteTask() throws Exception {
        Future<?> future = trackingExecutor.submit(
                operation,
                "TestTask",
                () -> {
                }
        );

        future.get(1, TimeUnit.SECONDS);

        List<Task> tasks =
                registry.findTasksByOperation(operation.getId());

        assertEquals(1, tasks.size());

        Task task = tasks.getFirst();

        assertEquals(TaskState.COMPLETED, task.getState());
        assertEquals(Instant.now(clock), task.getStartedAt());
        assertEquals(Instant.now(clock), task.getFinishedAt());
    }

    @Test
    void submitShouldPropagateExceptionAndFailTask() {
        Future<?> future = trackingExecutor.submit(
                operation,
                "TestTask",
                () -> {
                    throw new RuntimeException(
                            "Some runtime exception"
                    );
                }
        );

        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> future.get(1, TimeUnit.SECONDS)
        );

        assertInstanceOf(
                RuntimeException.class,
                exception.getCause()
        );

        Task task = registry
                .findTasksByOperation(operation.getId())
                .getFirst();

        assertEquals(TaskState.FAILED, task.getState());
        assertEquals(Instant.now(clock), task.getStartedAt());
        assertEquals(Instant.now(clock), task.getFinishedAt());
    }

    @Test
    void submitShouldPropagateErrorAndFailTask() {
        Future<?> future = trackingExecutor.submit(
                operation,
                "TestTask",
                () -> {
                    throw new AssertionError("Some error");
                }
        );

        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> future.get(1, TimeUnit.SECONDS)
        );

        assertInstanceOf(
                AssertionError.class,
                exception.getCause()
        );

        Task task = registry
                .findTasksByOperation(operation.getId())
                .getFirst();

        assertEquals(TaskState.FAILED, task.getState());
    }

    @Test
    void submitShouldPreserveOriginalFailureWhenFailTransitionThrows()
            throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch continueExecution = new CountDownLatch(1);

        Future<?> future = trackingExecutor.submit(
                operation,
                "TestTask",
                () -> {
                    started.countDown();

                    try {
                        continueExecution.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(exception);
                    }

                    throw new AssertionError("Original failure");
                }
        );

        assertTrue(started.await(1, TimeUnit.SECONDS));

        Task task = registry
                .findTasksByOperation(operation.getId())
                .getFirst();

        task.complete(Instant.now(clock));

        continueExecution.countDown();

        ExecutionException executionException = assertThrows(
                ExecutionException.class,
                () -> future.get(1, TimeUnit.SECONDS)
        );

        Throwable original = executionException.getCause();
        Throwable[] suppressed = original.getSuppressed();

        assertInstanceOf(AssertionError.class, original);
        assertEquals("Original failure", original.getMessage());

        assertEquals(1, suppressed.length);
        assertInstanceOf(
                IllegalStateException.class,
                suppressed[0]
        );
    }
}
