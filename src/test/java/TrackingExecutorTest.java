import io.nikitoo0os.TrackingExecutorService;
import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import io.nikitoo0os.entity.enums.TaskState;
import io.nikitoo0os.factory.TrackingRunnableFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TrackingExecutorTest {

    @Test
    void submitShouldTrackAndCompleteTask() throws InterruptedException, TimeoutException, ExecutionException {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        registry.registerOperation(operation);

        ExecutorService executor = Executors.newSingleThreadExecutor();

        TrackingRunnableFactory factory =
                new TrackingRunnableFactory(registry);

        TrackingExecutorService trackingExecutor =
                new TrackingExecutorService(executor, factory);

        try {
            Future<?> future = trackingExecutor.submit(
                    operation,
                    "TestTask",
                    () -> {
                    }
            );

            future.get(1, TimeUnit.SECONDS);
            List<Task> tasks = registry.findTasksByOperation(operation.getId());
            assertEquals(1, tasks.size());
            Task task = tasks.getFirst();
            assertEquals(TaskState.COMPLETED, task.getState());

        } finally {
            executor.shutdownNow();
        }
    }
}
