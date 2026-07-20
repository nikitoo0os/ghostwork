import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.entity.enums.TaskState;
import io.nikitoo0os.factory.TrackingRunnable;
import io.nikitoo0os.factory.TrackingRunnableFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TrackingRunnableFactoryTest {

    @Test
    void wrapShouldRegisterTaskAndCompleteItAfterExecution() {
        Registry registry = new Registry();
        TrackingRunnableFactory trackingRunnableFactory = new TrackingRunnableFactory(registry);
        Operation operation = new Operation("TestOperation");
        registry.registerOperation(operation);

        Runnable runnable = () -> {
            int c = 2 + 5;
        };

        TrackingRunnable trackingRunnable = trackingRunnableFactory.wrap(
                operation,
                "TestTask",
                runnable
        );

        List<Task> tasks = registry.findTasksByOperation(operation.getId());

        assertEquals(1, tasks.size());

        Task task = tasks.getFirst();

        assertEquals(TaskState.CREATED, task.getState());

        trackingRunnable.runnable().run();

        assertEquals(TaskState.COMPLETED, task.getState());
        assertNotNull(task.getStartedAt());
        assertNotNull(task.getFinishedAt());
    }
}
