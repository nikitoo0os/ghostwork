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
    void runningTaskShouldHaveRunningStateAndNullEndTime() {
        Instant startedAt = Instant.now(clock);

        task.start(startedAt);

        assertEquals(TaskState.RUNNING, task.getState());
        assertEquals(startedAt, task.getStartedAt());
        assertNull(task.getFinishedAt());
    }

    @Test
    void completedTaskShouldHaveCompletedStateAndExpectedTimes() {
        Instant startedAt = Instant.now(clock);
        Instant finishedAt = startedAt.plusSeconds(10);

        task.start(startedAt);
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
        task.start(Instant.now(clock));

        assertThrows(
                IllegalStateException.class,
                () -> task.start(Instant.now(clock))
        );
    }

    @Test
    void runningTaskShouldGoToFailedState() {
        Instant startedAt = Instant.now(clock);
        Instant finishedAt = startedAt.plusSeconds(10);

        task.start(startedAt);
        task.fail(finishedAt);

        assertEquals(TaskState.FAILED, task.getState());
        assertEquals(startedAt, task.getStartedAt());
        assertEquals(finishedAt, task.getFinishedAt());
    }

    @Test
    void taskShouldNotCompleteBeforeItStarted() {
        Instant startedAt = Instant.now(clock);
        Instant finishedAt = startedAt.minusSeconds(1);

        task.start(startedAt);

        assertThrows(
                IllegalArgumentException.class,
                () -> task.complete(finishedAt)
        );
    }

    @Test
    void taskShouldNotFailBeforeItStarted() {
        Instant startedAt = Instant.now(clock);
        Instant finishedAt = startedAt.minusSeconds(1);

        task.start(startedAt);

        assertThrows(
                IllegalArgumentException.class,
                () -> task.fail(finishedAt)
        );
    }

    @Test
    void taskShouldRejectNullStartTime() {
        assertThrows(
                NullPointerException.class,
                () -> task.start(null)
        );
    }

    @Test
    void taskShouldRejectNullCompletionTime() {
        task.start(Instant.now(clock));

        assertThrows(
                NullPointerException.class,
                () -> task.complete(null)
        );
    }

    @Test
    void taskShouldRejectNullFailureTime() {
        task.start(Instant.now(clock));

        assertThrows(
                NullPointerException.class,
                () -> task.fail(null)
        );
    }

    @Test
    void concurrentCompletionShouldAllowOnlyOneSuccessfulTransition()
            throws InterruptedException {
        task.start(Instant.now(clock));

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
}

