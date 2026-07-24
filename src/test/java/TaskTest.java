import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.entity.enums.TaskState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TaskTest {

    private Clock clock;
    private Operation operation;
    private Task task;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
                Instant.parse("2026-01-01T10:00:00Z"),
                ZoneOffset.UTC
        );

        operation = new Operation("Test Operation");
        task = new Task("Test task", operation);
    }

    @Test
    void newTaskShouldHaveCreatedStateWithoutTimes() {
        assertEquals(TaskState.CREATED, task.getState());
        assertNull(task.getStartedAt());
        assertNull(task.getFinishedAt());
    }

    @Test
    void submittedTaskShouldHaveSubmittedStateWithoutTimes() {
        task.submit();

        assertEquals(TaskState.SUBMITTED, task.getState());
        assertNull(task.getStartedAt());
        assertNull(task.getFinishedAt());
    }

    @Test
    void runningTaskShouldHaveRunningStateAndNullEndTime() {
        Instant startedAt = Instant.now(clock);

        startTask(startedAt);

        assertEquals(TaskState.RUNNING, task.getState());
        assertEquals(startedAt, task.getStartedAt());
        assertNull(task.getFinishedAt());
    }

    @Test
    void completedTaskShouldHaveCompletedStateAndExpectedTimes() {
        Instant startedAt = Instant.now(clock);
        Instant finishedAt = startedAt.plusSeconds(10);

        startTask(startedAt);
        task.complete(finishedAt);

        assertEquals(TaskState.COMPLETED, task.getState());
        assertEquals(startedAt, task.getStartedAt());
        assertEquals(finishedAt, task.getFinishedAt());
    }

    @Test
    void createdTaskShouldNotGoToCompleteState() {
        assertThrows(
                IllegalStateException.class,
                () -> task.complete(Instant.now(clock))
        );
    }

    @Test
    void createdTaskShouldNotGoToFailState() {
        assertThrows(
                IllegalStateException.class,
                () -> task.fail(Instant.now(clock))
        );
    }

    @Test
    void runningTaskShouldNotStartAgain() {
        startTask(Instant.now(clock));

        assertThrows(
                IllegalStateException.class,
                () -> startTask(Instant.now(clock))
        );
    }

    @Test
    void runningTaskShouldGoToFailedState() {
        Instant startedAt = Instant.now(clock);
        Instant finishedAt = startedAt.plusSeconds(10);

        startTask(startedAt);
        task.fail(finishedAt);

        assertEquals(TaskState.FAILED, task.getState());
        assertEquals(startedAt, task.getStartedAt());
        assertEquals(finishedAt, task.getFinishedAt());
    }

    @Test
    void taskShouldNotCompleteBeforeItStarted() {
        Instant startedAt = Instant.now(clock);
        Instant finishedAt = startedAt.minusSeconds(1);

        startTask(startedAt);

        assertThrows(
                IllegalArgumentException.class,
                () -> task.complete(finishedAt)
        );
    }

    @Test
    void taskShouldNotFailBeforeItStarted() {
        Instant startedAt = Instant.now(clock);
        Instant finishedAt = startedAt.minusSeconds(1);

        startTask(startedAt);

        assertThrows(
                IllegalArgumentException.class,
                () -> task.fail(finishedAt)
        );
    }

    @Test
    void taskShouldRejectNullStartTime() {
        assertThrows(
                NullPointerException.class,
                () -> startTask(null)
        );
    }

    @Test
    void taskShouldRejectNullCompletionTime() {
        startTask(Instant.now(clock));

        assertThrows(
                NullPointerException.class,
                () -> task.complete(null)
        );
    }

    @Test
    void taskShouldRejectNullFailureTime() {
        startTask(Instant.now(clock));

        assertThrows(
                NullPointerException.class,
                () -> task.fail(null)
        );
    }

    @Test
    void concurrentCompletionShouldAllowOnlyOneSuccessfulTransition()
            throws InterruptedException {
        startTask(Instant.now(clock));

        AtomicInteger successCount = new AtomicInteger();

        Thread t1 = new Thread(() -> {
            try {
                task.complete(Instant.now(clock));
                successCount.incrementAndGet();
            } catch (IllegalStateException ignored) {
                // Another thread completed the task first.
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                task.fail(Instant.now(clock));
                successCount.incrementAndGet();
            } catch (IllegalStateException ignored) {
                // Another thread completed the task first.
            }
        });

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        assertEquals(1, successCount.get());

        TaskState taskState = task.getState();

        assertTrue(
                taskState == TaskState.COMPLETED
                        || taskState == TaskState.FAILED
        );

        assertNotNull(task.getStartedAt());
        assertNotNull(task.getFinishedAt());
    }


    @Test
    void createdTaskShouldGoToCancelledState() {
        Instant cancelledAt = Instant.now(clock);

        task.cancel(cancelledAt);

        assertEquals(TaskState.CANCELLED, task.getState());
        assertNull(task.getStartedAt());
        assertEquals(cancelledAt, task.getFinishedAt());
        assertTrue(task.isFinished());
    }

    @Test
    void runningTaskShouldGoToCancelledState() {
        Instant startedAt = Instant.now(clock);
        Instant finishedAt = startedAt.plusSeconds(10);

        startTask(startedAt);
        task.cancel(finishedAt);

        assertEquals(TaskState.CANCELLED, task.getState());
        assertEquals(startedAt, task.getStartedAt());
        assertEquals(finishedAt, task.getFinishedAt());
        assertTrue(task.isFinished());
    }

    @Test
    void taskShouldNotCancelBeforeItStarted() {
        Instant startedAt = Instant.now(clock);
        Instant finishedAt = startedAt.minusSeconds(1);

        startTask(startedAt);

        assertThrows(
                IllegalArgumentException.class,
                () -> task.cancel(finishedAt)
        );
    }

    @Test
    void taskShouldRejectNullCancellationTime() {
        startTask(Instant.now(clock));

        assertThrows(
                NullPointerException.class,
                () -> task.cancel(null)
        );
    }

    @Test
    void createdTaskShouldGoToRejectedState() {
        Instant rejectedAt = Instant.now(clock);

        task.reject(rejectedAt);

        assertEquals(TaskState.REJECTED, task.getState());
        assertNull(task.getStartedAt());
        assertEquals(rejectedAt, task.getFinishedAt());
        assertTrue(task.isFinished());
    }

    @Test
    void runningTaskShouldNotGoToRejectedState() {
        startTask(Instant.now(clock));

        assertThrows(
                IllegalStateException.class,
                () -> task.reject(Instant.now(clock))
        );
    }

    @Test
    void completedTaskShouldNotGoToRejectedState() {
        Instant startedAt = Instant.now(clock);
        Instant finishedAt = startedAt.plusSeconds(10);

        startTask(startedAt);
        task.complete(finishedAt);

        assertThrows(
                IllegalStateException.class,
                () -> task.reject(finishedAt.plusSeconds(1))
        );
    }

    @Test
    void rejectedTaskShouldNotStart() {
        task.reject(Instant.now(clock));

        assertThrows(
                IllegalStateException.class,
                () -> startTask(Instant.now(clock))
        );
    }

    @Test
    void taskShouldRejectNullRejectionTime() {
        assertThrows(
                NullPointerException.class,
                () -> task.reject(null)
        );
    }

    @Test
    void concurrentFinishShouldAllowOnlyOneSuccessfulTransition()
            throws InterruptedException {
        startTask(Instant.now(clock));

        AtomicInteger successCount = new AtomicInteger();

        Thread t1 = new Thread(() -> {
            try {
                task.complete(Instant.now(clock));
                successCount.incrementAndGet();
            } catch (IllegalStateException ignored) {
                // Another thread finished the task first.
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                task.fail(Instant.now(clock));
                successCount.incrementAndGet();
            } catch (IllegalStateException ignored) {
                // Another thread finished the task first.
            }
        });

        Thread t3 = new Thread(() -> {
            try {
                task.cancel(Instant.now(clock));
                successCount.incrementAndGet();
            } catch (IllegalStateException ignored) {
                // Another thread finished the task first.
            }
        });

        t1.start();
        t2.start();
        t3.start();

        t1.join();
        t2.join();
        t3.join();

        assertEquals(1, successCount.get());

        TaskState taskState = task.getState();

        assertTrue(
                taskState == TaskState.COMPLETED
                        || taskState == TaskState.FAILED
                        || taskState == TaskState.CANCELLED
        );

        assertNotNull(task.getStartedAt());
        assertNotNull(task.getFinishedAt());
        assertTrue(task.isFinished());
    }

    @Test
    void concurrentStartAndRejectShouldAllowOnlyOneSuccessfulTransition()
            throws InterruptedException {
        AtomicInteger successCount = new AtomicInteger();

        Thread t1 = new Thread(() -> {
            try {
                startTask(Instant.now(clock));
                successCount.incrementAndGet();
            } catch (IllegalStateException ignored) {
                // Another thread moved the task out of CREATED first.
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                task.reject(Instant.now(clock));
                successCount.incrementAndGet();
            } catch (IllegalStateException ignored) {
                // Another thread moved the task out of CREATED first.
            }
        });

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        assertEquals(1, successCount.get());

        TaskState taskState = task.getState();

        assertTrue(
                taskState == TaskState.RUNNING
                        || taskState == TaskState.REJECTED
        );

        if (taskState == TaskState.RUNNING) {
            assertNotNull(task.getStartedAt());
            assertNull(task.getFinishedAt());
            assertFalse(task.isFinished());
        }

        if (taskState == TaskState.REJECTED) {
            assertNull(task.getStartedAt());
            assertNotNull(task.getFinishedAt());
            assertTrue(task.isFinished());
        }
    }

    @Test
    void concurrentStartAndCancelShouldLeaveTaskInValidState()
            throws InterruptedException {
        AtomicInteger successCount = new AtomicInteger();

        Thread t1 = new Thread(() -> {
            try {
                startTask(Instant.now(clock));
                successCount.incrementAndGet();
            } catch (IllegalStateException ignored) {
                // Another thread cancelled the task before it started.
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                task.cancel(Instant.now(clock));
                successCount.incrementAndGet();
            } catch (IllegalStateException ignored) {
                // Another thread moved the task to an incompatible state first.
            }
        });

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        assertTrue(
                successCount.get() == 1 || successCount.get() == 2
        );

        TaskState taskState = task.getState();

        assertTrue(
                taskState == TaskState.RUNNING
                        || taskState == TaskState.CANCELLED
        );

        if (taskState == TaskState.RUNNING) {
            assertEquals(1, successCount.get());
            assertNotNull(task.getStartedAt());
            assertNull(task.getFinishedAt());
            assertFalse(task.isFinished());
        }

        if (taskState == TaskState.CANCELLED) {
            assertNotNull(task.getFinishedAt());
            assertTrue(task.isFinished());

            if (task.getStartedAt() == null) {
                assertEquals(1, successCount.get());
            } else {
                assertEquals(2, successCount.get());
            }
        }

    }

    private void startTask(Instant startedAt) {
        if (task.getState() == TaskState.CREATED) {
            task.submit();
        }
        task.start(startedAt);
    }
}

