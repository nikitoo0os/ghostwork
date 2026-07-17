import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.entity.enums.TaskState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TaskTest {

    @Test
    void newTaskShouldHaveCreatedStateWithoutTimes(){
        Operation operation = new Operation("Test Operation");
        Task task = new Task("Test task", operation);

        assertEquals(
                TaskState.CREATED,
                task.getState()
        );
        assertNull(task.getStartedAt());
        assertNull(task.getFinishedAt());
    }

    @Test
    void runningTaskShouldHaveRunningStateAndNullEndTime(){
        Operation operation = new Operation("Test Operation");
        Task task = new Task("Test task", operation);
        task.start();

        assertEquals(
                TaskState.RUNNING,
                task.getState()
        );
        assertNotNull(task.getStartedAt());
        assertNull(task.getFinishedAt());
    }

    @Test
    void completedTaskShouldHaveCompletedStateAndNotNullEndTime(){
        Operation operation = new Operation("Test Operation");
        Task task = new Task("Test task", operation);
        task.start();
        task.complete();

        assertEquals(
                TaskState.COMPLETED,
                task.getState()
        );
        assertNotNull(task.getStartedAt());
        assertNotNull(task.getFinishedAt());
    }

    @Test
    void createdTaskShouldNotGoToCompleteState(){
        Operation operation = new Operation("Test Operation");
        Task task = new Task("Test task", operation);
        assertThrows(IllegalStateException.class, task::complete);
    }
    @Test
    void createdTaskShouldNotGoToFailState(){
        Operation operation = new Operation("Test Operation");
        Task task = new Task("Test task", operation);
        assertThrows(IllegalStateException.class, task::fail);
    }
    @Test
    void runningTaskShouldNotStart(){
        Operation operation = new Operation("Test Operation");
        Task task = new Task("Test task", operation);
        task.start();
        assertThrows(IllegalStateException.class, task::start);

    }

    @Test
    void runningTaskShouldGoToFailedState(){
        Operation operation = new Operation("Test Operation");
        Task task = new Task("Test task", operation);
        task.start();
        task.fail();

        assertNotNull(task.getStartedAt());
        assertEquals(TaskState.FAILED, task.getState());
        assertNotNull(task.getFinishedAt());
    }

    @Test
    void concurrentCompletionShouldAllowOnlyOneSuccessfulTransition() throws InterruptedException {
        Operation operation = new Operation("TestOperation");
        Task task = new Task("TestTask", operation);
        task.start();

        AtomicInteger successCount = new AtomicInteger(0);

        Thread t1 = new Thread(() -> {
            try {
                task.complete();
                successCount.incrementAndGet();
            } catch (IllegalStateException _) {
                // Another thread completed the task first.
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                task.fail();
                successCount.incrementAndGet();
            } catch (IllegalStateException _) {
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
