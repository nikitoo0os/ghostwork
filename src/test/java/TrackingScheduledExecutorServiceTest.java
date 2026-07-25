import io.nikitoo0os.GhostWork;
import io.nikitoo0os.RetentionPolicy;
import io.nikitoo0os.scheduling.ScheduleExecutionState;
import io.nikitoo0os.scheduling.ScheduleExecutionView;
import io.nikitoo0os.scheduling.ScheduleId;
import io.nikitoo0os.scheduling.ScheduleRetentionPolicy;
import io.nikitoo0os.scheduling.ScheduleOptions;
import io.nikitoo0os.scheduling.ScheduleMetadata;
import io.nikitoo0os.scheduling.ScheduleEventType;
import io.nikitoo0os.scheduling.ScheduleState;
import io.nikitoo0os.scheduling.ScheduleType;
import io.nikitoo0os.scheduling.ScheduleView;
import io.nikitoo0os.scheduling.TrackedScheduledFuture;
import io.nikitoo0os.scheduling.TrackingScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackingScheduledExecutorServiceTest {
    private final ScheduledExecutorService delegate =
            Executors.newScheduledThreadPool(2);
    private final GhostWork ghostWork = GhostWork.create(delegate);
    private final TrackingScheduledExecutorService scheduler =
            ghostWork.decorateScheduler(delegate);

    @AfterEach
    void tearDown() throws InterruptedException {
        delegate.shutdownNow();
        delegate.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    void oneTimeExecutionShouldCreateOperationAndRootTask() throws Exception {
        ScheduledFuture<String> future = scheduler.schedule(
                () -> "done",
                0,
                TimeUnit.MILLISECONDS
        );

        assertEquals("done", future.get(2, TimeUnit.SECONDS));

        ScheduleView schedule = onlySchedule();
        assertEquals(ScheduleType.ONE_TIME, schedule.type());
        assertEquals(ScheduleState.COMPLETED, schedule.state());
        assertEquals(1, schedule.statistics().completedExecutions());

        ScheduleExecutionView execution = onlyExecution(schedule.id());
        assertEquals(ScheduleExecutionState.COMPLETED, execution.state());
        assertNotNull(execution.operationId());
        assertNotNull(execution.rootTaskId());
        assertEquals(1, ghostWork.tasks(execution.operationId()).size());
    }

    @Test
    void scheduleEventsShouldBeOrderedAndListenerFailuresIsolated()
            throws Exception {
        List<ScheduleEventType> events = new CopyOnWriteArrayList<>();
        ghostWork.addScheduleListener(event -> {
            throw new IllegalStateException("listener failure");
        });
        ghostWork.addScheduleListener(event -> events.add(event.type()));

        scheduler.schedule(
                () -> {
                },
                0,
                TimeUnit.MILLISECONDS
        ).get(2, TimeUnit.SECONDS);

        assertEquals(
                List.of(
                        ScheduleEventType.SCHEDULE_CREATED,
                        ScheduleEventType.EXECUTION_EXPECTED,
                        ScheduleEventType.SCHEDULE_ACTIVATED,
                        ScheduleEventType.EXECUTION_STARTED,
                        ScheduleEventType.EXECUTION_COMPLETED,
                        ScheduleEventType.SCHEDULE_COMPLETED
                ),
                events
        );
    }

    @Test
    void nestedSubmitShouldBelongToExecutionOperation() throws Exception {
        CountDownLatch nestedFinished = new CountDownLatch(1);

        scheduler.schedule(() -> {
            ghostWork.executor().submit("nested", nestedFinished::countDown);
        }, 0, TimeUnit.MILLISECONDS).get(2, TimeUnit.SECONDS);

        assertTrue(nestedFinished.await(2, TimeUnit.SECONDS));
        ScheduleExecutionView execution =
                onlyExecution(onlySchedule().id());
        await(() -> ghostWork.tasks(execution.operationId()).size() == 2);

        List<String> taskNames = ghostWork.tasks(execution.operationId())
                .stream()
                .map(task -> task.name())
                .toList();
        assertTrue(taskNames.getFirst().endsWith(" scheduled invocation"));
        assertEquals("nested", taskNames.get(1));
    }

    @Test
    void fixedRateShouldCreateDistinctExecutions() throws Exception {
        CountDownLatch runs = new CountDownLatch(3);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                runs::countDown,
                0,
                10,
                TimeUnit.MILLISECONDS
        );

        assertTrue(runs.await(2, TimeUnit.SECONDS));
        assertTrue(future.cancel(false));

        ScheduleView schedule = onlySchedule();
        assertEquals(ScheduleType.FIXED_RATE, schedule.type());
        assertEquals(ScheduleState.CANCELLED, schedule.state());
        assertTrue(schedule.statistics().startedExecutions() >= 3);
        assertTrue(ghostWork.scheduleExecutions(schedule.id()).size() >= 3);
    }

    @Test
    void fixedDelayShouldCreateDistinctExecutionsAfterCompletion()
            throws Exception {
        CountDownLatch runs = new CountDownLatch(2);
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                runs::countDown,
                0,
                20,
                TimeUnit.MILLISECONDS
        );

        assertTrue(runs.await(2, TimeUnit.SECONDS));
        future.cancel(false);

        ScheduleView schedule = onlySchedule();
        assertEquals(ScheduleType.FIXED_DELAY, schedule.type());
        List<ScheduleExecutionView> completed =
                ghostWork.scheduleExecutions(schedule.id()).stream()
                        .filter(execution ->
                                execution.state()
                                == ScheduleExecutionState.COMPLETED)
                        .toList();
        assertTrue(completed.size() >= 2);
    }

    @Test
    void sameJdkPeriodicTaskShouldNotOverlap() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicBoolean overlap = new AtomicBoolean();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch runs = new CountDownLatch(2);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            int concurrent = active.incrementAndGet();
            if (concurrent > 1) {
                overlap.set(true);
            }
            try {
                if (runs.getCount() == 2) {
                    firstStarted.countDown();
                    releaseFirst.await();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
                runs.countDown();
            }
        }, 0, 5, TimeUnit.MILLISECONDS);

        assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
        assertEquals(2, runs.getCount());
        releaseFirst.countDown();
        assertTrue(runs.await(2, TimeUnit.SECONDS));
        future.cancel(true);

        assertFalse(overlap.get());
        assertEquals(
                0,
                onlySchedule().statistics().overlappingExecutions()
        );
    }

    @Test
    void runningExecutionShouldBecomeLongRunningBeforeCompletion()
            throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        scheduler.schedule(
                new ScheduleOptions(
                        "LongRun",
                        ScheduleMetadata.manual("long-run"),
                        Duration.ZERO,
                        Duration.ofNanos(1)
                ),
                () -> {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                },
                0,
                TimeUnit.MILLISECONDS
        );

        assertTrue(started.await(2, TimeUnit.SECONDS));
        await(() -> onlyExecution(onlySchedule().id()).longRunning());
        release.countDown();

        assertEquals(
                1,
                onlySchedule().statistics().longRunningExecutions()
        );
    }

    @Test
    void cancellationBeforeStartShouldCancelExpectedExecution() {
        ScheduledFuture<?> future = scheduler.schedule(
                () -> {
                    throw new AssertionError("Must not run");
                },
                1,
                TimeUnit.DAYS
        );

        assertTrue(future.cancel(false));

        ScheduleView schedule = onlySchedule();
        assertEquals(ScheduleState.CANCELLED, schedule.state());
        assertEquals(
                ScheduleExecutionState.CANCELLED,
                onlyExecution(schedule.id()).state()
        );
    }

    @Test
    void cancelFalseShouldLetActiveExecutionComplete() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, 0, 1, TimeUnit.DAYS);

        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertTrue(future.cancel(false));
        release.countDown();
        await(() -> onlySchedule().statistics().completedExecutions() == 1);

        assertEquals(
                ScheduleExecutionState.COMPLETED,
                ghostWork.scheduleExecutions(onlySchedule().id()).stream()
                        .filter(execution -> execution.operationId() != null)
                        .findFirst()
                        .orElseThrow()
                        .state()
        );
    }

    @Test
    void rejectionShouldLeaveFailedDefinitionAndRethrow() {
        ScheduledExecutorService rejectedDelegate =
                Executors.newSingleThreadScheduledExecutor();
        rejectedDelegate.shutdown();
        GhostWork rejectedGhostWork = GhostWork.create(rejectedDelegate);
        TrackingScheduledExecutorService rejected =
                rejectedGhostWork.decorateScheduler(rejectedDelegate);

        assertThrows(
                RejectedExecutionException.class,
                () -> rejected.schedule(
                        () -> {
                        },
                        0,
                        TimeUnit.MILLISECONDS
                )
        );

        assertEquals(
                ScheduleState.FAILED,
                rejectedGhostWork.schedules().getFirst().state()
        );
    }

    @Test
    void cleanupShouldRemoveOldExecutionDetailsButKeepAggregates()
            throws Exception {
        CountDownLatch runs = new CountDownLatch(3);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                runs::countDown,
                0,
                10,
                TimeUnit.MILLISECONDS
        );
        assertTrue(runs.await(2, TimeUnit.SECONDS));
        future.cancel(false);
        ScheduleView beforeCleanup = onlySchedule();

        assertTrue(ghostWork.cleanupSchedules(
                new ScheduleRetentionPolicy(1, Duration.ofDays(1))
        ) >= 2);

        assertEquals(
                1,
                ghostWork.scheduleExecutions(beforeCleanup.id()).size()
        );
        assertTrue(
                ghostWork.schedule(beforeCleanup.id())
                        .statistics()
                        .completedExecutions() >= 3
        );
    }

    @Test
    void trackedFutureShouldExposeStableScheduleId() throws Exception {
        ScheduledFuture<?> future = scheduler.schedule(
                () -> {
                },
                0,
                TimeUnit.MILLISECONDS
        );
        future.get(2, TimeUnit.SECONDS);

        assertTrue(future instanceof TrackedScheduledFuture<?>);
        ScheduleId id = ((TrackedScheduledFuture<?>) future).scheduleId();
        assertEquals(id, onlySchedule().id());
    }

    @Test
    void trackedFutureShouldDelegateDelayAndOrdering() {
        ScheduledFuture<?> first = scheduler.schedule(
                () -> {
                },
                1,
                TimeUnit.DAYS
        );
        ScheduledFuture<?> second = scheduler.schedule(
                () -> {
                },
                2,
                TimeUnit.DAYS
        );

        assertTrue(first.getDelay(TimeUnit.HOURS) > 0);
        assertTrue(first.compareTo(second) < 0);
        first.cancel(false);
        second.cancel(false);
    }

    @Test
    void shutdownNowShouldExposePendingAndTerminationDiagnostics()
            throws Exception {
        scheduler.schedule(() -> {
        }, 1, TimeUnit.DAYS);

        List<Runnable> returned = scheduler.shutdownNow();
        assertFalse(returned.isEmpty());
        assertTrue(scheduler.awaitTermination(2, TimeUnit.SECONDS));

        var diagnostics = scheduler.shutdownDiagnostics().orElseThrow();
        assertEquals(
                io.nikitoo0os.scheduling.SchedulerShutdownMethod.SHUTDOWN_NOW,
                diagnostics.method()
        );
        assertTrue(diagnostics.expectedExecutions() >= 1);
        assertEquals(returned.size(), diagnostics.returnedQueuedTasks());
        assertTrue(diagnostics.terminated());
        assertNotNull(diagnostics.terminationObservedAt());
        assertEquals(ScheduleState.CANCELLED, onlySchedule().state());
    }

    @Test
    void scheduleRetentionShouldLeaseOperationDetails() throws Exception {
        ScheduledExecutorService localDelegate =
                Executors.newSingleThreadScheduledExecutor();
        GhostWork localGhostWork = GhostWork.create(
                localDelegate,
                new RetentionPolicy(
                        0,
                        Duration.ofNanos(1),
                        Duration.ofMinutes(1)
                )
        );
        try {
            CountDownLatch runs = new CountDownLatch(2);
            ScheduledFuture<?> future = localGhostWork
                    .decorateScheduler(localDelegate)
                    .scheduleAtFixedRate(
                            runs::countDown,
                            0,
                            10,
                            TimeUnit.MILLISECONDS
                    );
            assertTrue(runs.await(2, TimeUnit.SECONDS));
            future.cancel(false);

            assertEquals(0, localGhostWork.cleanup());
            assertEquals(
                    1,
                    localGhostWork.cleanupSchedules(
                            new ScheduleRetentionPolicy(
                                    1,
                                    Duration.ofDays(1)
                            )
                    )
            );
            assertEquals(1, localGhostWork.cleanup());
        } finally {
            localDelegate.shutdownNow();
        }
    }

    private ScheduleView onlySchedule() {
        await(() -> ghostWork.schedules().size() == 1);
        return ghostWork.schedules().getFirst();
    }

    private ScheduleExecutionView onlyExecution(ScheduleId scheduleId) {
        await(() -> ghostWork.scheduleExecutions(scheduleId).size() == 1);
        return ghostWork.scheduleExecutions(scheduleId).getFirst();
    }

    private static void await(Check check) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!check.done() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(check.done(), "Condition was not satisfied in time");
    }

    @FunctionalInterface
    private interface Check {
        boolean done();
    }
}
