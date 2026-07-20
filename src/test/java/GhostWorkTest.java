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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertEquals(3, events.size());

        assertEquals(GhostWorkEventType.TASK_STARTED, events.get(0).type());
        assertEquals(GhostWorkEventType.TASK_COMPLETED, events.get(1).type());
        assertEquals(GhostWorkEventType.OPERATION_COMPLETED, events.get(2).type());
    }
}