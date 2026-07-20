import io.nikitoo0os.TrackingExecutorService;
import io.nikitoo0os.context.OperationContext;
import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.entity.enums.TaskState;
import io.nikitoo0os.event.GhostWorkEvent;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.event.GhostWorkEventType;
import io.nikitoo0os.factory.TrackingCallableFactory;
import io.nikitoo0os.factory.TrackingRunnableFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
    private final GhostWorkEventPublisher eventPublisher =
            new GhostWorkEventPublisher();

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
                Instant.parse("2026-01-01T10:00:00Z"),
                ZoneOffset.UTC
        );

        registry = new Registry();

        operation = new Operation("TestOperation");
        registry.registerOperation(operation);

        executor = Executors.newFixedThreadPool(2);

        trackingExecutor = new TrackingExecutorService(
                executor,
                new TrackingRunnableFactory(registry, clock, eventPublisher),
                new TrackingCallableFactory(registry, clock, eventPublisher),
                clock
        );
    }

    @AfterEach
    void tearDown() {
        OperationContext.clear();
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

    @Test
    void contextSubmitRunnableShouldTrackTaskForCurrentOperation()
            throws Exception {
        OperationContext.set(operation);

        Future<?> future = trackingExecutor.submit(
                "ContextTask",
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
    void contextSubmitCallableShouldTrackTaskForCurrentOperation()
            throws Exception {
        OperationContext.set(operation);

        Future<String> future = trackingExecutor.submit(
                "ContextCallableTask",
                () -> "done"
        );

        assertEquals("done", future.get(1, TimeUnit.SECONDS));

        List<Task> tasks =
                registry.findTasksByOperation(operation.getId());

        assertEquals(1, tasks.size());

        Task task = tasks.getFirst();

        assertEquals(TaskState.COMPLETED, task.getState());
        assertEquals(Instant.now(clock), task.getStartedAt());
        assertEquals(Instant.now(clock), task.getFinishedAt());
    }

    @Test
    void contextSubmitRunnableShouldThrowWhenOperationContextIsEmpty() {
        assertThrows(
                IllegalStateException.class,
                () -> trackingExecutor.submit(
                        "ContextTask",
                        () -> {
                        }
                )
        );

        assertEquals(
                List.of(),
                registry.findTasksByOperation(operation.getId())
        );
    }

    @Test
    void contextSubmitCallableShouldThrowWhenOperationContextIsEmpty() {
        assertThrows(
                IllegalStateException.class,
                () -> trackingExecutor.submit(
                        "ContextCallableTask",
                        () -> "done"
                )
        );

        assertEquals(
                List.of(),
                registry.findTasksByOperation(operation.getId())
        );
    }

    @Test
    void contextSubmitRunnableShouldExposeOperationContextInsideDelegate()
            throws Exception {
        OperationContext.set(operation);

        Future<?> future = trackingExecutor.submit(
                "ContextTask",
                () -> assertEquals(
                        operation,
                        OperationContext.current().orElseThrow()
                )
        );

        future.get(1, TimeUnit.SECONDS);
    }

    @Test
    void contextSubmitCallableShouldExposeOperationContextInsideDelegate()
            throws Exception {
        OperationContext.set(operation);

        Future<Operation> future = trackingExecutor.submit(
                "ContextCallableTask",
                () -> OperationContext.current().orElseThrow()
        );

        assertEquals(
                operation,
                future.get(1, TimeUnit.SECONDS)
        );
    }

    @Test
    void nestedContextSubmitShouldTrackInnerTaskForSameOperation()
            throws Exception {
        OperationContext.set(operation);

        Future<?> outerFuture = trackingExecutor.submit(
                "OuterTask",
                () -> {
                    Future<?> innerFuture = trackingExecutor.submit(
                            "InnerTask",
                            () -> {
                            }
                    );

                    try {
                        innerFuture.get(1, TimeUnit.SECONDS);
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                }
        );

        outerFuture.get(1, TimeUnit.SECONDS);

        List<Task> tasks =
                registry.findTasksByOperation(operation.getId());

        assertEquals(2, tasks.size());

        assertTrue(
                tasks.stream().anyMatch(task ->
                        task.getName().equals("OuterTask")
                                && task.getState() == TaskState.COMPLETED
                )
        );

        assertTrue(
                tasks.stream().anyMatch(task ->
                        task.getName().equals("InnerTask")
                                && task.getState() == TaskState.COMPLETED
                )
        );
    }

    @Test
    void shutdownShouldDelegateToUnderlyingExecutor()
            throws InterruptedException {
        trackingExecutor.shutdown();

        assertTrue(trackingExecutor.isShutdown());

        assertTrue(
                trackingExecutor.awaitTermination(
                        1,
                        TimeUnit.SECONDS
                )
        );

        assertTrue(trackingExecutor.isTerminated());
    }

    @Test
    void executeShouldTrackAndCompleteTask()
            throws InterruptedException {
        CountDownLatch finished = new CountDownLatch(1);

        trackingExecutor.execute(
                operation,
                "ExecuteTask",
                finished::countDown
        );

        assertTrue(finished.await(1, TimeUnit.SECONDS));

        List<Task> tasks =
                registry.findTasksByOperation(operation.getId());

        assertEquals(1, tasks.size());

        Task task = tasks.getFirst();

        assertEquals(TaskState.COMPLETED, task.getState());
        assertEquals(Instant.now(clock), task.getStartedAt());
        assertEquals(Instant.now(clock), task.getFinishedAt());
    }

    @Test
    void contextExecuteShouldTrackTaskForCurrentOperation()
            throws InterruptedException {
        CountDownLatch finished = new CountDownLatch(1);

        OperationContext.set(operation);

        trackingExecutor.execute(
                "ContextExecuteTask",
                finished::countDown
        );

        assertTrue(finished.await(1, TimeUnit.SECONDS));

        List<Task> tasks =
                registry.findTasksByOperation(operation.getId());

        assertEquals(1, tasks.size());

        Task task = tasks.getFirst();

        assertEquals(TaskState.COMPLETED, task.getState());
        assertEquals(Instant.now(clock), task.getStartedAt());
        assertEquals(Instant.now(clock), task.getFinishedAt());
    }

    @Test
    void contextExecuteShouldThrowWhenOperationContextIsEmpty() {
        assertThrows(
                IllegalStateException.class,
                () -> trackingExecutor.execute(
                        "ContextExecuteTask",
                        () -> {
                        }
                )
        );

        assertEquals(
                List.of(),
                registry.findTasksByOperation(operation.getId())
        );
    }

    @Test
    void submitRunnableShouldRejectTaskWhenDelegateRejects() {
        trackingExecutor.shutdown();

        assertThrows(
                RuntimeException.class,
                () -> trackingExecutor.submit(
                        operation,
                        "RejectedRunnableTask",
                        () -> {
                        }
                )
        );

        List<Task> tasks =
                registry.findTasksByOperation(operation.getId());

        assertEquals(1, tasks.size());

        Task task = tasks.getFirst();

        assertEquals(TaskState.REJECTED, task.getState());
        assertNull(task.getStartedAt());
        assertEquals(Instant.now(clock), task.getFinishedAt());
        assertTrue(task.isFinished());
    }

    @Test
    void submitCallableShouldRejectTaskWhenDelegateRejects() {
        trackingExecutor.shutdown();

        assertThrows(
                RuntimeException.class,
                () -> trackingExecutor.submit(
                        operation,
                        "RejectedCallableTask",
                        () -> "done"
                )
        );

        List<Task> tasks =
                registry.findTasksByOperation(operation.getId());

        assertEquals(1, tasks.size());

        Task task = tasks.getFirst();

        assertEquals(TaskState.REJECTED, task.getState());
        assertNull(task.getStartedAt());
        assertEquals(Instant.now(clock), task.getFinishedAt());
        assertTrue(task.isFinished());
    }

    @Test
    void executeShouldRejectTaskWhenDelegateRejects() {
        trackingExecutor.shutdown();

        assertThrows(
                RuntimeException.class,
                () -> trackingExecutor.execute(
                        operation,
                        "RejectedExecuteTask",
                        () -> {
                        }
                )
        );

        List<Task> tasks =
                registry.findTasksByOperation(operation.getId());

        assertEquals(1, tasks.size());

        Task task = tasks.getFirst();

        assertEquals(TaskState.REJECTED, task.getState());
        assertNull(task.getStartedAt());
        assertEquals(Instant.now(clock), task.getFinishedAt());
        assertTrue(task.isFinished());
    }
    @Test
    void submitRunnableShouldPublishTaskStartedAndCompletedEvents()
            throws Exception {
        List<GhostWorkEvent> events = new ArrayList<>();

        eventPublisher.addListener(events::add);

        Operation operation = new Operation("ImportUsers");
        registry.registerOperation(operation);

        trackingExecutor.submit(
                operation,
                "ReadFile",
                () -> {
                }
        ).get(1, TimeUnit.SECONDS);

        assertEquals(2, events.size());

        GhostWorkEvent started = events.get(0);
        GhostWorkEvent completed = events.get(1);

        assertEquals(GhostWorkEventType.TASK_STARTED, started.type());
        assertEquals("ImportUsers", started.operation().name());
        assertEquals("ReadFile", started.task().name());
        assertEquals(TaskState.RUNNING, started.task().state());
        assertNull(started.failure());

        assertEquals(GhostWorkEventType.TASK_COMPLETED, completed.type());
        assertEquals("ImportUsers", completed.operation().name());
        assertEquals("ReadFile", completed.task().name());
        assertEquals(TaskState.COMPLETED, completed.task().state());
        assertNull(completed.failure());
    }

    @Test
    void submitCallableShouldPublishTaskFailedEvent()
            throws Exception {
        List<GhostWorkEvent> events = new ArrayList<>();

        eventPublisher.addListener(events::add);

        Operation operation = new Operation("ImportUsers");
        registry.registerOperation(operation);

        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> trackingExecutor.submit(
                        operation,
                        "ReadFile",
                        () -> {
                            throw new RuntimeException("Read failed");
                        }
                ).get(1, TimeUnit.SECONDS)
        );

        assertInstanceOf(RuntimeException.class, exception.getCause());

        assertEquals(2, events.size());

        GhostWorkEvent started = events.get(0);
        GhostWorkEvent failed = events.get(1);

        assertEquals(GhostWorkEventType.TASK_STARTED, started.type());
        assertEquals(TaskState.RUNNING, started.task().state());

        assertEquals(GhostWorkEventType.TASK_FAILED, failed.type());
        assertEquals("ImportUsers", failed.operation().name());
        assertEquals("ReadFile", failed.task().name());
        assertEquals(TaskState.FAILED, failed.task().state());
        assertNotNull(failed.failure());
    }

    @Test
    void submitCallableShouldPublishTaskStartedAndCompletedEvents()
            throws Exception {
        List<GhostWorkEvent> events = new ArrayList<>();

        eventPublisher.addListener(events::add);

        Operation operation = new Operation("ImportUsers");
        registry.registerOperation(operation);

        String result = trackingExecutor.submit(
                operation,
                "ReadFile",
                () -> "done"
        ).get(1, TimeUnit.SECONDS);

        assertEquals("done", result);
        assertEquals(2, events.size());

        GhostWorkEvent started = events.get(0);
        GhostWorkEvent completed = events.get(1);

        assertEquals(GhostWorkEventType.TASK_STARTED, started.type());
        assertEquals("ImportUsers", started.operation().name());
        assertEquals("ReadFile", started.task().name());
        assertEquals(TaskState.RUNNING, started.task().state());
        assertNull(started.failure());

        assertEquals(GhostWorkEventType.TASK_COMPLETED, completed.type());
        assertEquals("ImportUsers", completed.operation().name());
        assertEquals("ReadFile", completed.task().name());
        assertEquals(TaskState.COMPLETED, completed.task().state());
        assertNull(completed.failure());
    }

    @Test
    void submitRunnableShouldPublishTaskRejectedEvent() {
        List<GhostWorkEvent> events = new ArrayList<>();

        eventPublisher.addListener(events::add);

        trackingExecutor.shutdown();

        assertThrows(
                RuntimeException.class,
                () -> trackingExecutor.submit(
                        operation,
                        "RejectedRunnableTask",
                        () -> {
                        }
                )
        );

        assertEquals(1, events.size());

        GhostWorkEvent event = events.getFirst();

        assertEquals(GhostWorkEventType.TASK_REJECTED, event.type());
        assertEquals("TestOperation", event.operation().name());
        assertEquals("RejectedRunnableTask", event.task().name());
        assertEquals(TaskState.REJECTED, event.task().state());
        assertNull(event.failure());
    }

    @Test
    void submitCallableShouldPublishTaskRejectedEvent() {
        List<GhostWorkEvent> events = new ArrayList<>();

        eventPublisher.addListener(events::add);

        trackingExecutor.shutdown();

        assertThrows(
                RuntimeException.class,
                () -> trackingExecutor.submit(
                        operation,
                        "RejectedCallableTask",
                        () -> "done"
                )
        );

        assertEquals(1, events.size());

        GhostWorkEvent event = events.getFirst();

        assertEquals(GhostWorkEventType.TASK_REJECTED, event.type());
        assertEquals("TestOperation", event.operation().name());
        assertEquals("RejectedCallableTask", event.task().name());
        assertEquals(TaskState.REJECTED, event.task().state());
        assertNull(event.failure());
    }
}
