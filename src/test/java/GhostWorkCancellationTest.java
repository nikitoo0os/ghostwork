import io.nikitoo0os.*;
import io.nikitoo0os.entity.enums.OperationState;
import io.nikitoo0os.entity.enums.TaskState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GhostWorkCancellationTest {
    private final ExecutorService delegate = Executors.newFixedThreadPool(3);
    private final GhostWork ghostWork = GhostWork.create(delegate);

    @AfterEach
    void close() {
        delegate.shutdownNow();
    }

    @Test
    void runningTaskShouldObserveRequestWithoutBecomingCancelled() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<CancellationToken> observed = new AtomicReference<>();
        OperationHandle operation = ghostWork.startOperation("request", metadata());
        Future<?> future;
        try (var ignored = operation.openScope()) {
            future = ghostWork.executor().submit("worker", () -> {
                observed.set(GhostWorkContext.currentCancellationToken());
                started.countDown();
                await(release);
            });
        }
        assertTrue(started.await(2, TimeUnit.SECONDS));
        UUID taskId = ghostWork.tasks(operation.id()).getFirst().id();

        CancellationResult result = ghostWork.cancelTask(
                taskId,
                CancellationOptions.requestOnly(CancellationCause.USER_REQUEST)
        );

        assertTrue(result.requestAccepted());
        assertTrue(observed.get().isCancellationRequested());
        assertEquals(TaskState.RUNNING, ghostWork.tasks(operation.id())
                .getFirst().state());
        release.countDown();
        future.get(2, TimeUnit.SECONDS);
    }

    @Test
    void cooperativeCancellationShouldFinishTaskAsCancelled() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        OperationHandle operation = ghostWork.startOperation("cooperative", metadata());
        Future<?> future;
        try (var ignored = operation.openScope()) {
            future = ghostWork.executor().submit("worker", () -> {
                CancellationToken token =
                        GhostWorkContext.currentCancellationToken();
                started.countDown();
                while (true) {
                    token.throwIfCancellationRequested();
                    Thread.onSpinWait();
                }
            });
        }
        assertTrue(started.await(2, TimeUnit.SECONDS));
        UUID taskId = ghostWork.tasks(operation.id()).getFirst().id();

        ghostWork.cancelTask(
                taskId,
                CancellationOptions.requestOnly(CancellationCause.USER_REQUEST)
        );

        assertThrows(ExecutionException.class, () ->
                future.get(2, TimeUnit.SECONDS));
        assertEquals(TaskState.CANCELLED, ghostWork.tasks(operation.id())
                .getFirst().state());
        assertEquals(
                CancellationStatus.CANCELLED,
                ghostWork.taskCancellation(taskId).status()
        );
    }

    @Test
    void nestedChildShouldInheritParentCancellation() throws Exception {
        CountDownLatch childStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Future<?>> childFuture = new AtomicReference<>();
        OperationHandle operation = ghostWork.startOperation("nested", metadata());
        Future<?> parent;
        try (var ignored = operation.openScope()) {
            parent = ghostWork.executor().submit("parent", () -> {
                childFuture.set(ghostWork.executor().submit("child", () -> {
                    childStarted.countDown();
                    await(release);
                }));
                await(release);
            });
        }
        assertTrue(childStarted.await(2, TimeUnit.SECONDS));
        var diagnostics = ghostWork.taskDiagnostics(operation.id());
        UUID parentId = diagnostics.stream()
                .filter(task -> task.taskName().equals("parent"))
                .findFirst().orElseThrow().taskId();
        UUID childId = diagnostics.stream()
                .filter(task -> task.taskName().equals("child"))
                .findFirst().orElseThrow().taskId();

        ghostWork.cancelTask(
                parentId,
                CancellationOptions.requestOnly(CancellationCause.USER_REQUEST)
        );

        assertEquals(
                parentId,
                ghostWork.taskDiagnostics(operation.id()).stream()
                        .filter(task -> task.taskId().equals(childId))
                        .findFirst().orElseThrow().parentTaskId()
        );
        assertEquals(
                CancellationCause.PARENT_TASK_CANCELLED,
                ghostWork.taskCancellation(childId).cancellationCause()
        );
        release.countDown();
        parent.get(2, TimeUnit.SECONDS);
        childFuture.get().get(2, TimeUnit.SECONDS);
    }

    @Test
    void futureCancellationShouldPropagateToNestedChild() throws Exception {
        CountDownLatch childStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Future<?>> childFuture = new AtomicReference<>();
        OperationHandle operation = ghostWork.startOperation(
                "future-parent",
                metadata()
        );
        Future<?> parent;
        try (var ignored = operation.openScope()) {
            parent = ghostWork.executor().submit("parent", () -> {
                childFuture.set(ghostWork.executor().submit("child", () -> {
                    childStarted.countDown();
                    await(release);
                }));
                await(release);
            });
        }
        assertTrue(childStarted.await(2, TimeUnit.SECONDS));
        UUID childId = ghostWork.taskDiagnostics(operation.id()).stream()
                .filter(task -> task.taskName().equals("child"))
                .findFirst()
                .orElseThrow()
                .taskId();

        assertTrue(parent.cancel(false));

        assertEquals(
                CancellationCause.PARENT_TASK_CANCELLED,
                ghostWork.taskCancellation(childId).cancellationCause()
        );
        release.countDown();
        childFuture.get().get(2, TimeUnit.SECONDS);
    }

    @Test
    void detachedTaskShouldOutliveOperationWithoutBeingGhost() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        OperationHandle operation = ghostWork.startOperation("detached", metadata());
        Future<?> future;
        try (var ignored = operation.openScope()) {
            future = ghostWork.executor().submit(
                    TaskOptions.detached("audit"),
                    () -> {
                        started.countDown();
                        await(release);
                    }
            );
        }
        assertTrue(started.await(2, TimeUnit.SECONDS));
        operation.complete();

        assertTrue(ghostWork.ghostTasks(operation.id()).isEmpty());
        assertEquals(TaskCancellationMode.DETACHED,
                ghostWork.taskCancellation(
                        ghostWork.tasks(operation.id()).getFirst().id()
                ).mode());
        release.countDown();
        future.get(2, TimeUnit.SECONDS);
    }

    @Test
    void explicitOperationCancellationShouldPreserveRunningReality()
            throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        OperationHandle operation = ghostWork.startOperation("cancel", metadata());
        try (var ignored = operation.openScope()) {
            ghostWork.executor().submit("resistant", () -> {
                started.countDown();
                while (release.getCount() > 0) {
                    try {
                        release.await();
                    } catch (InterruptedException caught) {
                        interrupted.countDown();
                    }
                }
            });
        }
        assertTrue(started.await(2, TimeUnit.SECONDS));

        CancellationResult result = operation.cancel();

        assertEquals(OperationState.RUNNING, result.operationStateBefore());
        assertEquals(OperationState.CANCELLED, operation.view().state());
        assertEquals(1, result.runningTasksTargeted());
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        assertEquals(TaskState.RUNNING,
                ghostWork.tasks(operation.id()).getFirst().state());
        release.countDown();
    }

    @Test
    void gracePeriodShouldClassifyCancellationResistantTaskOnce()
            throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        OperationHandle operation = ghostWork.startOperation("grace", metadata());
        try (var ignored = operation.openScope()) {
            ghostWork.executor().submit("worker", () -> {
                started.countDown();
                await(release);
            });
        }
        assertTrue(started.await(2, TimeUnit.SECONDS));
        UUID taskId = ghostWork.tasks(operation.id()).getFirst().id();
        ghostWork.cancelTask(
                taskId,
                CancellationOptions.requestOnly(CancellationCause.USER_REQUEST)
        );

        assertEquals(1, ghostWork.refreshCancellationDiagnostics(Duration.ZERO));
        assertEquals(0, ghostWork.refreshCancellationDiagnostics(Duration.ZERO));
        assertEquals(CancellationStatus.IGNORED,
                ghostWork.taskCancellation(taskId).status());
        assertEquals(
                ChildCancellationPolicy.REQUEST_CANCELLATION,
                ghostWork.taskCancellation(taskId).policy()
        );
        release.countDown();
    }

    @Test
    void taskContextShouldBeClearedFromPooledThread() throws Exception {
        OperationHandle operation = ghostWork.startOperation("context", metadata());
        try (var ignored = operation.openScope()) {
            ghostWork.executor().submit("tracked", () ->
                    assertNotEquals(
                            CancellationToken.none(),
                            GhostWorkContext.currentCancellationToken()
                    )
            ).get(2, TimeUnit.SECONDS);
        }

        assertSame(
                CancellationToken.none(),
                delegate.submit(GhostWorkContext::currentCancellationToken)
                        .get(2, TimeUnit.SECONDS)
        );
    }

    @Test
    void unknownTargetsShouldReturnNotFound() {
        assertFalse(ghostWork.cancelTask(UUID.randomUUID()).found());
        assertFalse(ghostWork.cancelOperation(UUID.randomUUID()).found());
    }

    @Test
    void queuedTaskShouldBeCancelledBeforeItStarts() throws Exception {
        ExecutorService single = Executors.newSingleThreadExecutor();
        GhostWork local = GhostWork.create(single);
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        AtomicBoolean taskRan = new AtomicBoolean();
        single.submit(() -> {
            blockerStarted.countDown();
            await(releaseBlocker);
        });
        assertTrue(blockerStarted.await(2, TimeUnit.SECONDS));

        OperationHandle operation = local.startOperation("queued", metadata());
        Future<?> future;
        try (var ignored = operation.openScope()) {
            future = local.executor().submit(
                    "queued-task",
                    () -> taskRan.set(true)
            );
        }
        UUID taskId = local.tasks(operation.id()).getFirst().id();

        assertTrue(local.cancelTask(taskId).futureCancellationsAccepted() == 1);
        assertTrue(future.isCancelled());
        assertEquals(
                TaskState.CANCELLED,
                local.tasks(operation.id()).getFirst().state()
        );
        releaseBlocker.countDown();
        single.shutdownNow();
        assertFalse(taskRan.get());
    }

    @Test
    void swallowedInterruptShouldCompleteWithCancellationMetadata()
            throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch returned = new CountDownLatch(1);
        OperationHandle operation = ghostWork.startOperation(
                "swallowed-interrupt",
                metadata()
        );
        try (var ignored = operation.openScope()) {
            ghostWork.executor().submit("worker", () -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException expected) {
                    returned.countDown();
                }
            });
        }
        assertTrue(started.await(2, TimeUnit.SECONDS));
        UUID taskId = ghostWork.tasks(operation.id()).getFirst().id();

        ghostWork.cancelTask(taskId);

        assertTrue(returned.await(2, TimeUnit.SECONDS));
        awaitState(operation.id(), TaskState.COMPLETED);
        TaskCancellationView cancellation =
                ghostWork.taskCancellation(taskId);
        assertTrue(cancellation.interruptRequested());
        assertFalse(cancellation.interruptObserved());
    }

    @Test
    void ordinaryFailureAfterRequestShouldRemainFailure() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch fail = new CountDownLatch(1);
        OperationHandle operation = ghostWork.startOperation(
                "failure-after-request",
                metadata()
        );
        Future<?> future;
        try (var ignored = operation.openScope()) {
            future = ghostWork.executor().submit("worker", () -> {
                started.countDown();
                await(fail);
                throw new IllegalStateException("business failure");
            });
        }
        assertTrue(started.await(2, TimeUnit.SECONDS));
        UUID taskId = ghostWork.tasks(operation.id()).getFirst().id();
        ghostWork.cancelTask(
                taskId,
                CancellationOptions.requestOnly(CancellationCause.USER_REQUEST)
        );
        fail.countDown();

        assertThrows(ExecutionException.class, () ->
                future.get(2, TimeUnit.SECONDS));
        assertEquals(
                TaskState.FAILED,
                ghostWork.tasks(operation.id()).getFirst().state()
        );
    }

    @Test
    void concurrentCancellationShouldPublishAndInvokeCallbackOnce()
            throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        AtomicInteger requestEvents = new AtomicInteger();
        OperationHandle operation = ghostWork.startOperation(
                "concurrent-cancel",
                metadata()
        );
        try (var ignored = operation.openScope()) {
            ghostWork.executor().submit("worker", () -> {
                try (var registration = GhostWorkContext
                        .currentCancellationToken()
                        .onCancellation(callbacks::incrementAndGet)) {
                    started.countDown();
                    await(release);
                }
            });
        }
        assertTrue(started.await(2, TimeUnit.SECONDS));
        UUID taskId = ghostWork.tasks(operation.id()).getFirst().id();
        ghostWork.addEventListener(event -> {
            if (event.type() == io.nikitoo0os.event.GhostWorkEventType
                    .TASK_CANCELLATION_REQUESTED) {
                requestEvents.incrementAndGet();
                ghostWork.taskCancellation(taskId);
            }
        });

        ExecutorService cancellers = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<CancellationResult>> results =
                    java.util.stream.IntStream.range(0, 8)
                            .mapToObj(ignored -> cancellers.submit(() -> {
                                ready.countDown();
                                assertTrue(go.await(2, TimeUnit.SECONDS));
                                return ghostWork.cancelTask(
                                        taskId,
                                        CancellationOptions.requestOnly(
                                                CancellationCause.USER_REQUEST
                                        )
                                );
                            }))
                            .toList();
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            go.countDown();
            assertEquals(
                    1,
                    results.stream()
                            .map(this::get)
                            .filter(CancellationResult::requestAccepted)
                            .count()
            );
        } finally {
            cancellers.shutdownNow();
            release.countDown();
        }
        assertEquals(1, callbacks.get());
        assertEquals(1, requestEvents.get());
    }

    @Test
    void listenerFailureShouldNotBreakCancellationOrTerminalTask()
            throws Exception {
        OperationHandle operation = ghostWork.startOperation(
                "terminal",
                metadata()
        );
        try (var ignored = operation.openScope()) {
            ghostWork.executor().submit("worker", () -> {
            }).get(2, TimeUnit.SECONDS);
        }
        UUID taskId = ghostWork.tasks(operation.id()).getFirst().id();
        ghostWork.addEventListener(event -> {
            throw new IllegalStateException("listener failure");
        });

        CancellationResult result = ghostWork.cancelTask(taskId);

        assertFalse(result.requestAccepted());
        assertEquals(
                TaskState.COMPLETED,
                ghostWork.tasks(operation.id()).getFirst().state()
        );
    }

    private <T> T get(Future<T> future) {
        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void awaitState(UUID operationId, TaskState expected)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (ghostWork.tasks(operationId).getFirst().state() == expected) {
                return;
            }
            Thread.onSpinWait();
        }
        fail("Task did not reach state " + expected);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test latch");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static OperationMetadata metadata() {
        return new OperationMetadata() {
        };
    }
}
