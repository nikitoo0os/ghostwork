import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.entity.WrappedRunnable;
import io.nikitoo0os.entity.enums.TaskState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WrappedRunnableTest {

    @Test
    void delegateWillBeExecutedSuccessfully(){
        Operation operation = new Operation("Test Operation");
        Task task = new Task("Test task", operation);

        Runnable runnable = () -> {
            int a = 2;
            int b = 3;
            int c = a+b;
        };

        WrappedRunnable wrappedRunnable = new WrappedRunnable(runnable, task);

        wrappedRunnable.run();
        assertEquals(
                TaskState.COMPLETED,
                task.getState()
        );
        assertNotNull(task.getStartedAt());
        assertNotNull(task.getFinishedAt());
    }

    @Test
    void delegateWillBeExecutedFailed(){
        Operation operation = new Operation("Test Operation");
        Task task = new Task("Test task", operation);

        Runnable runnable = () -> {
            throw new RuntimeException();
        };

        WrappedRunnable wrappedRunnable = new WrappedRunnable(runnable, task);

        assertThrows(RuntimeException.class, wrappedRunnable::run);
        assertEquals(
                TaskState.FAILED,
                task.getState()
        );
        assertNotNull(task.getStartedAt());
        assertNotNull(task.getFinishedAt());

    }
}
