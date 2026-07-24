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
                clock,
                eventPublisher,
                registry
        );
    }

    @AfterEach
    void tearDown() {
        OperationContext.clear();
        executor.shutdownNow();
    }

    @Test
    void runTaskShouldTrackRunnableOnCallingThread() {
        Thread callingThread = Thread.currentThread();
        Thread[] taskThread = new Thread[1];

        try (OperationContext.Scope ignored = OperationContext.open(operation)) {
            trackingExecutor.runTask(
                    "InlineRunnable",
                    () -> taskThread[0] = Thread.currentThread()
            );
        }

        Task task = registry.findTasksByOperation(operation.getId()).getFirst();

        assertSame(callingThread, taskThread[0]);
        assertEquals(TaskState.COMPLETED, task.getState());
        assertEquals(Instant.now(clock), task.getStartedAt());
        assertEquals(Instant.now(clock), task.getFinishedAt());
    }

    @Test
    void callTaskShouldTrackCallableAndReturnResult() throws Exception {
        String result;

        try (OperationContext.Scope ignored = OperationContext.open(operation)) {
            result = trackingExecutor.callTask(
                    "InlineCallable",
                    () -> "done"
            );
        }

        Task task = registry.findTasksByOperation(operation.getId()).getFirst();

        assertEquals("done", result);
        assertEquals(TaskState.COMPLETED, task.getState());
    }

    @Test
    void callTaskShouldPreserveOriginalFailureAndFailTask() {
        IllegalArgumentException original =
                new IllegalArgumentException("business failure");

        IllegalArgumentException thrown;
        try (OperationContext.Scope ignored = OperationContext.open(operation)) {
            thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> trackingExecutor.callTask(
                            "FailingInlineCallable",
                            () -> {
                                throw original;
                            }
                    )
            );
        }

        Task task = registry.findTasksByOperation(operation.getId()).getFirst();

        assertSame(original, thrown);
        assertEquals(TaskState.FAILED, task.getState());
    }

    @Test
    void inlineTaskShouldRequireOperationContext() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> trackingExecutor.runTask("NoContext", () -> {
                })
        );

        assertEquals("OperationContext is empty", exception.getMessage());
        assertTrue(registry.findTasksByOperation(operation.getId()).isEmpty());
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
    void contextSubmitRunnableShouldCreateImplicitOperationWhenOperationContextIsEmpty()
            throws Exception {
        Future<?> future = trackingExecutor.submit(
                "ContextTask",
                () -> {
                }
        );

        future.get(1, TimeUnit.SECONDS);

        Operation implicitOperation = registry.findOperations()
                .stream()
                .filter(operation -> operation.getName().equals("Implicit:ContextTask"))
                .findFirst()
                .orElseThrow();

        List<Task> tasks =
                registry.findTasksByOperation(implicitOperation.getId());

        assertEquals(OperationState.COMPLETED, implicitOperation.getState());
        assertEquals(1, tasks.size());
        assertEquals("ContextTask", tasks.getFirst().getName());
        assertEquals(TaskState.COMPLETED, tasks.getFirst().getState());
    }

    @Test
    void contextSubmitCallableShouldCreateImplicitOperationWhenOperationContextIsEmpty()
            throws Exception {
        Future<String> future = trackingExecutor.submit(
                "ContextCallableTask",
                () -> "done"
        );

        assertEquals("done", future.get(1, TimeUnit.SECONDS));

        Operation implicitOperation = registry.findOperations()
                .stream()
                .filter(operation -> operation.getName().equals("Implicit:ContextCallableTask"))
                .findFirst()
                .orElseThrow();

        List<Task> tasks =
                registry.findTasksByOperation(implicitOperation.getId());

        assertEquals(OperationState.COMPLETED, implicitOperation.getState());
        assertEquals(1, tasks.size());
        assertEquals("ContextCallableTask", tasks.getFirst().getName());
        assertEquals(TaskState.COMPLETED, tasks.getFirst().getState());
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

        awaitState(task, TaskState.COMPLETED);
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

        awaitState(task, TaskState.COMPLETED);
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
    @Test
    void cancelledQueuedRunnableShouldMoveTaskToCancelledState()
            throws Exception {
        executor.shutdownNow();

        executor = Executors.newSingleThreadExecutor();

        trackingExecutor = new TrackingExecutorService(
                executor,
                new TrackingRunnableFactory(registry, clock, eventPublisher),
                new TrackingCallableFactory(registry, clock, eventPublisher),
                clock,
                eventPublisher
        );

        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);

        trackingExecutor.submit(
                operation,
                "Blocker",
                () -> {
                    blockerStarted.countDown();
                    try {
                        releaseBlocker.await(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
        );

        assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));

        Future<?> future = trackingExecutor.submit(
                operation,
                "QueuedTask",
                () -> {
                }
        );

        assertTrue(future.cancel(false));

        releaseBlocker.countDown();

        Task task = registry.findTasksByOperation(operation.getId())
                .stream()
                .filter(candidate -> candidate.getName().equals("QueuedTask"))
                .findFirst()
                .orElseThrow();

        assertEquals(TaskState.CANCELLED, task.getState());
        assertNull(task.getStartedAt());
        assertEquals(Instant.now(clock), task.getFinishedAt());
        assertTrue(task.isFinished());
    }

    @Test
    void cancellingQueuedRunnableShouldPublishTaskCancelledEvent()
            throws Exception {
        List<GhostWorkEvent> events = new ArrayList<>();

        eventPublisher.addListener(events::add);

        executor.shutdownNow();

        executor = Executors.newSingleThreadExecutor();

        trackingExecutor = new TrackingExecutorService(
                executor,
                new TrackingRunnableFactory(registry, clock, eventPublisher),
                new TrackingCallableFactory(registry, clock, eventPublisher),
                clock,
                eventPublisher
        );

        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);

        trackingExecutor.submit(
                operation,
                "Blocker",
                () -> {
                    blockerStarted.countDown();
                    try {
                        releaseBlocker.await(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
        );

        assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));

        Future<?> future = trackingExecutor.submit(
                operation,
                "QueuedTask",
                () -> {
                }
        );

        assertTrue(future.cancel(false));

        releaseBlocker.countDown();

        GhostWorkEvent cancelled = events.stream()
                .filter(event -> event.type() == GhostWorkEventType.TASK_CANCELLED)
                .findFirst()
                .orElseThrow();

        assertEquals("TestOperation", cancelled.operation().name());
        assertEquals("QueuedTask", cancelled.task().name());
        assertEquals(TaskState.CANCELLED, cancelled.task().state());
        assertNull(cancelled.failure());
    }

    @Test
    void completedTaskShouldNotBeCancelledThroughFuture()
            throws Exception {
        Future<?> future = trackingExecutor.submit(
                operation,
                "CompletedTask",
                () -> {
                }
        );

        future.get(1, TimeUnit.SECONDS);

        assertFalse(future.cancel(false));

        Task task = registry.findTasksByOperation(operation.getId())
                .getFirst();

        assertEquals(TaskState.COMPLETED, task.getState());
    }

    private static void awaitState(Task task, TaskState expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);

        while (task.getState() != expected && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
    }
}
