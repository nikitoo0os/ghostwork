import io.nikitoo0os.GhostWork;
import io.nikitoo0os.GhostWorkMonitor;
import io.nikitoo0os.GhostWorkReport;
import io.nikitoo0os.OperationView;
import io.nikitoo0os.TaskView;
import io.nikitoo0os.entity.enums.OperationState;
import io.nikitoo0os.entity.enums.TaskState;
import io.nikitoo0os.event.GhostWorkEvent;
import io.nikitoo0os.event.GhostWorkEventType;
import io.nikitoo0os.operation.OperationDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class GhostWorkTest {

    private final ExecutorService executor =
            Executors.newFixedThreadPool(2);

    private final GhostWork ghostWork =
            GhostWork.create(executor);

    @AfterEach
    void tearDown() {
        ghostWork.executor().shutdownNow();
    }

    @Test
    void callShouldTrackSubmittedTaskThroughPublicFacade()
            throws Exception {
        ghostWork.call(
                "ImportUsers",
                () -> {
                    ghostWork.executor()
                            .submit(
                                    "ReadFile",
                                    () -> {
                                    }
                            )
                            .get(1, TimeUnit.SECONDS);

                    return null;
                }
        );

        OperationView operation = findOperationByName("ImportUsers");
        List<TaskView> tasks = ghostWork.tasks(operation.id());

        assertEquals(OperationState.COMPLETED, operation.state());
        assertEquals(1, tasks.size());

        TaskView task = tasks.getFirst();

        assertEquals("ReadFile", task.name());
        assertEquals(TaskState.COMPLETED, task.state());
        assertNotNull(task.startedAt());
        assertNotNull(task.finishedAt());
    }

    @Test
    void runShouldTrackSubmittedTaskThroughPublicFacade()
            throws Exception {
        ghostWork.run(
                "Cleanup",
                () -> {
                    try {
                        ghostWork.executor()
                                .submit(
                                        "DeleteTempFiles",
                                        () -> {
                                        }
                                )
                                .get(1, TimeUnit.SECONDS);
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                }
        );

        OperationView operation = findOperationByName("Cleanup");
        List<TaskView> tasks = ghostWork.tasks(operation.id());

        assertEquals(OperationState.COMPLETED, operation.state());
        assertEquals(1, tasks.size());

        TaskView task = tasks.getFirst();

        assertEquals("DeleteTempFiles", task.name());
        assertEquals(TaskState.COMPLETED, task.state());
    }

    @Test
    void callShouldAcceptOperationDefinition()
            throws Exception {
        ghostWork.call(
                OperationDefinition.named("DefinitionOperation"),
                () -> {
                    ghostWork.executor()
                            .submit(
                                    "DefinitionTask",
                                    () -> {
                                    }
                            )
                            .get(1, TimeUnit.SECONDS);

                    return null;
                }
        );

        OperationView operation =
                findOperationByName("DefinitionOperation");

        List<TaskView> tasks = ghostWork.tasks(operation.id());

        assertEquals(OperationState.COMPLETED, operation.state());
        assertEquals(1, tasks.size());
        assertEquals("DefinitionTask", tasks.getFirst().name());
        assertEquals(TaskState.COMPLETED, tasks.getFirst().state());
    }

    @Test
    void ghostTasksShouldBeAvailableThroughPublicFacade()
            throws Exception {
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        AtomicReference<Future<?>> submittedTask =
                new AtomicReference<>();

        ghostWork.run(
                "GhostOperation",
                () -> {
                    submittedTask.set(
                            ghostWork.executor()
                                    .submit(
                                            "StillRunningTask",
                                            () -> {
                                                taskStarted.countDown();

                                                try {
                                                    releaseTask.await();
                                                } catch (InterruptedException exception) {
                                                    Thread.currentThread().interrupt();
                                                    throw new RuntimeException(exception);
                                                }
                                            }
                                    )
                    );

                    try {
                        taskStarted.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(exception);
                    }
                }
        );

        OperationView operation = findOperationByName("GhostOperation");

        List<TaskView> ghostTasks =
                ghostWork.ghostTasks(operation.id());

        assertEquals(OperationState.COMPLETED, operation.state());
        assertEquals(1, ghostTasks.size());
        assertEquals("StillRunningTask", ghostTasks.getFirst().name());
        assertEquals(TaskState.RUNNING, ghostTasks.getFirst().state());

        releaseTask.countDown();
        submittedTask.get().get(1, TimeUnit.SECONDS);
    }

    @Test
    void stuckTasksShouldRejectInvalidThresholdThroughPublicFacade() {
        ghostWork.run(
                "InvalidThresholdOperation",
                () -> {
                }
        );

        OperationView operation =
                findOperationByName("InvalidThresholdOperation");

        assertThrows(
                IllegalArgumentException.class,
                () -> ghostWork.stuckTasks(
                        operation.id(),
                        Duration.ZERO
                )
        );
    }

    private OperationView findOperationByName(String name) {
        return ghostWork.operations()
                .stream()
                .filter(operation -> operation.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void reportShouldContainOperationsAndTasks()
            throws Exception {
        ghostWork.call(
                "ReportOperation",
                () -> {
                    ghostWork.executor()
                            .submit(
                                    "ReportTask",
                                    () -> {
                                    }
                            )
                            .get(1, TimeUnit.SECONDS);

                    return null;
                }
        );

        GhostWorkReport report =
                ghostWork.report(Duration.ofSeconds(30));

        assertEquals(1, report.operations().size());
        assertEquals(1, report.tasks().size());
        assertTrue(report.ghostTasks().isEmpty());
        assertTrue(report.stuckTasks().isEmpty());

        assertEquals("ReportOperation", report.operations().getFirst().name());
        assertEquals("ReportTask", report.tasks().getFirst().name());
        assertEquals(TaskState.COMPLETED, report.tasks().getFirst().state());
    }

    @Test
    void reportShouldRejectZeroStuckThreshold() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ghostWork.report(Duration.ZERO)
        );
    }

    @Test
    void reportShouldRejectNegativeStuckThreshold() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ghostWork.report(Duration.ofSeconds(-1))
        );
    }

    @Test
    void reportShouldRejectNullStuckThreshold() {
        assertThrows(
                NullPointerException.class,
                () -> ghostWork.report(null)
        );
    }

    @Test
    void monitorShouldBeCreatedThroughPublicFacade() {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        try {
            GhostWorkMonitor monitor =
                    ghostWork.monitor(scheduler);

            assertNotNull(monitor);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void monitorShouldRejectNullScheduler() {
        assertThrows(
                NullPointerException.class,
                () -> ghostWork.monitor(null)
        );
    }

    @Test
    void addEventListenerShouldRejectNull() {
        assertThrows(
                NullPointerException.class,
                () -> ghostWork.addEventListener(null)
        );
    }

    @Test
    void removeEventListenerShouldRejectNull() {
        assertThrows(
                NullPointerException.class,
                () -> ghostWork.removeEventListener(null)
        );
    }

    @Test
    void eventListenerShouldReceiveOperationAndTaskEventsThroughPublicFacade()
            throws Exception {
        List<GhostWorkEvent> events = new ArrayList<>();

        ghostWork.addEventListener(events::add);

        ghostWork.call(
                "ImportUsers",
                () -> {
                    ghostWork.executor()
                            .submit(
                                    "ReadFile",
                                    () -> {
                                    }
                            )
                            .get(1, TimeUnit.SECONDS);

                    return null;
                }
        );

        assertEquals(4, events.size());

        assertEquals(GhostWorkEventType.TASK_SUBMITTED, events.get(0).type());
        assertEquals(GhostWorkEventType.TASK_STARTED, events.get(1).type());
        assertEquals(GhostWorkEventType.TASK_COMPLETED, events.get(2).type());
        assertEquals(GhostWorkEventType.OPERATION_COMPLETED, events.get(3).type());
    }

    @Test
    void cancelledTaskShouldBeVisibleThroughPublicFacade()
            throws Exception {
        ExecutorService singleThreadExecutor =
                Executors.newSingleThreadExecutor();

        GhostWork ghostWork = GhostWork.create(singleThreadExecutor);

        try {
            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch releaseBlocker = new CountDownLatch(1);
            AtomicReference<Future<?>> queuedFuture =
                    new AtomicReference<>();

            ghostWork.run(
                    "CancelOperation",
                    () -> {
                        ghostWork.executor().submit(
                                "Blocker",
                                () -> {
                                    blockerStarted.countDown();

                                    try {
                                        releaseBlocker.await(1, TimeUnit.SECONDS);
                                    } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                        );

                        try {
                            assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(exception);
                        }

                        queuedFuture.set(
                                ghostWork.executor().submit(
                                        "QueuedTask",
                                        () -> {
                                        }
                                )
                        );

                        assertTrue(queuedFuture.get().cancel(false));
                        releaseBlocker.countDown();
                    }
            );

            OperationView operation = ghostWork.operations()
                    .stream()
                    .filter(candidate -> candidate.name().equals("CancelOperation"))
                    .findFirst()
                    .orElseThrow();

            TaskView cancelledTask = ghostWork.tasks(operation.id())
                    .stream()
                    .filter(task -> task.name().equals("QueuedTask"))
                    .findFirst()
                    .orElseThrow();

            assertEquals(TaskState.CANCELLED, cancelledTask.state());
            assertNull(cancelledTask.startedAt());
            assertNotNull(cancelledTask.finishedAt());
        } finally {
            ghostWork.executor().shutdownNow();
        }
    }

    @Test
    void cancellingTaskShouldPublishTaskCancelledEventThroughPublicFacade()
            throws Exception {
        ExecutorService singleThreadExecutor =
                Executors.newSingleThreadExecutor();

        GhostWork ghostWork = GhostWork.create(singleThreadExecutor);

        try {
            List<GhostWorkEvent> events = new ArrayList<>();

            ghostWork.addEventListener(events::add);

            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch releaseBlocker = new CountDownLatch(1);

            ghostWork.run(
                    "CancelOperation",
                    () -> {
                        ghostWork.executor().submit(
                                "Blocker",
                                () -> {
                                    blockerStarted.countDown();

                                    try {
                                        releaseBlocker.await(1, TimeUnit.SECONDS);
                                    } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                        );

                        try {
                            assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(exception);
                        }

                        Future<?> queuedFuture = ghostWork.executor().submit(
                                "QueuedTask",
                                () -> {
                                }
                        );

                        assertTrue(queuedFuture.cancel(false));
                        releaseBlocker.countDown();
                    }
            );

            GhostWorkEvent cancelled = events.stream()
                    .filter(event -> event.type() == GhostWorkEventType.TASK_CANCELLED)
                    .findFirst()
                    .orElseThrow();

            assertEquals("CancelOperation", cancelled.operation().name());
            assertEquals("QueuedTask", cancelled.task().name());
            assertEquals(TaskState.CANCELLED, cancelled.task().state());
            assertNull(cancelled.task().startedAt());
            assertNotNull(cancelled.task().finishedAt());
            assertNull(cancelled.failure());
        } finally {
            ghostWork.executor().shutdownNow();
        }
    }
    @Test
    void reportShouldIncludeCancelledTaskButExcludeItFromGhostAndStuckTasks()
            throws Exception {
        ExecutorService singleThreadExecutor =
                Executors.newSingleThreadExecutor();

        GhostWork ghostWork = GhostWork.create(singleThreadExecutor);

        try {
            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch releaseBlocker = new CountDownLatch(1);
            AtomicReference<Future<?>> blockerFuture =
                    new AtomicReference<>();

            ghostWork.run(
                    "CancelReportOperation",
                    () -> {
                        blockerFuture.set(
                                ghostWork.executor().submit(
                                        "Blocker",
                                        () -> {
                                            blockerStarted.countDown();

                                            try {
                                                releaseBlocker.await(1, TimeUnit.SECONDS);
                                            } catch (InterruptedException exception) {
                                                Thread.currentThread().interrupt();
                                            }
                                        }
                                )
                        );

                        try {
                            assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(exception);
                        }

                        Future<?> queuedFuture = ghostWork.executor().submit(
                                "QueuedTask",
                                () -> {
                                }
                        );

                        assertTrue(queuedFuture.cancel(false));
                        releaseBlocker.countDown();
                    }
            );

            blockerFuture.get().get(1, TimeUnit.SECONDS);

            GhostWorkReport report =
                    ghostWork.report(Duration.ofMillis(1));

            TaskView cancelledTask = report.tasks()
                    .stream()
                    .filter(task -> task.name().equals("QueuedTask"))
                    .findFirst()
                    .orElseThrow();

            assertEquals(TaskState.CANCELLED, cancelledTask.state());
            assertTrue(report.ghostTasks().isEmpty());
            assertTrue(report.stuckTasks().isEmpty());
        } finally {
            ghostWork.executor().shutdownNow();
        }
    }
    @Test
    void submitRunnableWithoutActiveOperationShouldCreateImplicitOperation()
            throws Exception {
        ghostWork.executor()
                .submit(
                        "StandaloneTask",
                        () -> {
                        }
                )
                .get(1, TimeUnit.SECONDS);

        OperationView operation = ghostWork.operations()
                .stream()
                .filter(candidate -> candidate.name().equals("Implicit:StandaloneTask"))
                .findFirst()
                .orElseThrow();

        List<TaskView> tasks = ghostWork.tasks(operation.id());

        assertEquals(OperationState.COMPLETED, operation.state());
        assertEquals(1, tasks.size());
        assertEquals("StandaloneTask", tasks.getFirst().name());
        assertEquals(TaskState.COMPLETED, tasks.getFirst().state());
    }

    @Test
    void submitCallableWithoutActiveOperationShouldCreateImplicitOperation()
            throws Exception {
        String result = ghostWork.executor()
                .submit(
                        "StandaloneCallable",
                        () -> "done"
                )
                .get(1, TimeUnit.SECONDS);

        assertEquals("done", result);

        OperationView operation = ghostWork.operations()
                .stream()
                .filter(candidate -> candidate.name().equals("Implicit:StandaloneCallable"))
                .findFirst()
                .orElseThrow();

        List<TaskView> tasks = ghostWork.tasks(operation.id());

        assertEquals(OperationState.COMPLETED, operation.state());
        assertEquals(1, tasks.size());
        assertEquals("StandaloneCallable", tasks.getFirst().name());
        assertEquals(TaskState.COMPLETED, tasks.getFirst().state());
    }

    @Test
    void failedImplicitRunnableShouldFailImplicitOperation()
            throws Exception {
        Future<?> future = ghostWork.executor()
                .submit(
                        "FailingStandaloneTask",
                        () -> {
                            throw new RuntimeException("boom");
                        }
                );

        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> future.get(1, TimeUnit.SECONDS)
        );

        assertEquals("boom", exception.getCause().getMessage());

        OperationView operation = ghostWork.operations()
                .stream()
                .filter(candidate -> candidate.name().equals("Implicit:FailingStandaloneTask"))
                .findFirst()
                .orElseThrow();

        List<TaskView> tasks = ghostWork.tasks(operation.id());

        assertEquals(OperationState.FAILED, operation.state());
        assertEquals(1, tasks.size());
        assertEquals("FailingStandaloneTask", tasks.getFirst().name());
        assertEquals(TaskState.FAILED, tasks.getFirst().state());
    }
    @Test
    void implicitRunnableShouldPublishTaskAndOperationEvents()
            throws Exception {
        List<GhostWorkEvent> events = new ArrayList<>();

        ghostWork.addEventListener(events::add);

        ghostWork.executor()
                .submit(
                        "StandaloneTask",
                        () -> {
                        }
                )
                .get(1, TimeUnit.SECONDS);

        assertEquals(4, events.size());

        assertEquals(GhostWorkEventType.TASK_SUBMITTED, events.get(0).type());
        assertEquals(GhostWorkEventType.TASK_STARTED, events.get(1).type());
        assertEquals("Implicit:StandaloneTask", events.get(0).operation().name());
        assertEquals("StandaloneTask", events.get(0).task().name());
        assertEquals(TaskState.SUBMITTED, events.get(0).task().state());
        assertEquals(TaskState.RUNNING, events.get(1).task().state());

        assertEquals(GhostWorkEventType.TASK_COMPLETED, events.get(2).type());
        assertEquals("Implicit:StandaloneTask", events.get(2).operation().name());
        assertEquals("StandaloneTask", events.get(2).task().name());
        assertEquals(TaskState.COMPLETED, events.get(2).task().state());

        assertEquals(GhostWorkEventType.OPERATION_COMPLETED, events.get(3).type());
        assertEquals("Implicit:StandaloneTask", events.get(3).operation().name());
        assertEquals(OperationState.COMPLETED, events.get(3).operation().state());
    }
}
