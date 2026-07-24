import io.nikitoo0os.GhostWork;
import io.nikitoo0os.OperationView;
import io.nikitoo0os.TaskView;
import io.nikitoo0os.entity.enums.OperationState;
import io.nikitoo0os.entity.enums.TaskState;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.factory.TrackingCallableFactory;
import io.nikitoo0os.factory.TrackingRunnableFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

class TrackingExecutorServiceContractTest {

    private ExecutorService delegate;
    private GhostWork ghostWork;

    @BeforeEach
    void setUp() {
        delegate = Executors.newFixedThreadPool(3);
        ghostWork = GhostWork.create(delegate);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        delegate.shutdownNow();
        delegate.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    void executeShouldTrackAnImplicitOperation() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);

        ghostWork.executor().execute(completed::countDown);

        assertTrue(completed.await(1, TimeUnit.SECONDS));
        OperationView operation = awaitFinishedOperation();
        List<TaskView> tasks = ghostWork.tasks(operation.id());

        assertEquals(OperationState.COMPLETED, operation.state());
        assertEquals(1, tasks.size());
        assertEquals(TaskState.COMPLETED, tasks.getFirst().state());
    }

    @Test
    void submitRunnableWithResultShouldPreserveTheResult() throws Exception {
        Future<String> future = ghostWork.executor().submit(() -> {
        }, "done");

        assertEquals("done", future.get(1, TimeUnit.SECONDS));
        OperationView operation = awaitFinishedOperation();
        assertEquals(
                TaskState.COMPLETED,
                ghostWork.tasks(operation.id()).getFirst().state()
        );
    }

    @Test
    void invokeAllShouldGroupTasksUnderOneImplicitOperation()
            throws Exception {
        List<Future<Integer>> futures = ghostWork.executor().invokeAll(List.of(
                () -> 1,
                () -> 2,
                () -> 3
        ));

        assertEquals(List.of(1, 2, 3), futures.stream().map(future -> {
            try {
                return future.get();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }).toList());

        OperationView operation = ghostWork.operations().getFirst();
        assertEquals("Implicit:invokeAll", operation.name());
        assertEquals(OperationState.COMPLETED, operation.state());
        assertEquals(3, ghostWork.tasks(operation.id()).size());
    }

    @Test
    void timedInvokeAllShouldCancelUnfinishedTasksAndTimeOutOperation()
            throws Exception {
        CountDownLatch blocker = new CountDownLatch(1);
        Callable<String> blocked = () -> {
            blocker.await();
            return "late";
        };

        List<Future<String>> futures = ghostWork.executor().invokeAll(
                List.of(() -> "fast", blocked),
                30,
                TimeUnit.MILLISECONDS
        );

        OperationView operation = ghostWork.operations().getFirst();
        assertEquals(OperationState.TIMED_OUT, operation.state());
        assertEquals("fast", futures.getFirst().get());
        assertTrue(futures.get(1).isCancelled());
        assertTrue(ghostWork.tasks(operation.id()).stream()
                .anyMatch(task -> task.state() == TaskState.COMPLETED));
        assertTrue(ghostWork.tasks(operation.id()).stream()
                .anyMatch(task -> task.state() == TaskState.CANCELLED));
    }

    @Test
    void invokeAnyShouldReturnFirstSuccessAndCancelRemainingTasks()
            throws Exception {
        CountDownLatch blocker = new CountDownLatch(1);

        String result = ghostWork.executor().invokeAny(List.of(
                () -> {
                    blocker.await();
                    return "late";
                },
                () -> "winner"
        ));

        assertEquals("winner", result);
        OperationView operation = ghostWork.operations().getFirst();
        List<TaskView> tasks = ghostWork.tasks(operation.id());

        assertEquals(OperationState.COMPLETED, operation.state());
        assertTrue(tasks.stream()
                .anyMatch(task -> task.state() == TaskState.COMPLETED));
        assertTrue(tasks.stream()
                .anyMatch(task -> task.state() == TaskState.CANCELLED));
    }

    @Test
    void timedInvokeAnyShouldCancelTasksAndTimeOutOperation() {
        CountDownLatch blocker = new CountDownLatch(1);

        assertThrows(
                TimeoutException.class,
                () -> ghostWork.executor().invokeAny(
                        List.of(() -> {
                            blocker.await();
                            return "late";
                        }),
                        30,
                        TimeUnit.MILLISECONDS
                )
        );

        OperationView operation = ghostWork.operations().getFirst();
        assertEquals(OperationState.TIMED_OUT, operation.state());
        assertEquals(
                TaskState.CANCELLED,
                ghostWork.tasks(operation.id()).getFirst().state()
        );
    }

    @Test
    void lifecycleMethodsShouldDelegate() throws Exception {
        ghostWork.executor().shutdown();

        assertTrue(ghostWork.executor().isShutdown());
        assertTrue(ghostWork.executor().awaitTermination(
                1,
                TimeUnit.SECONDS
        ));
        assertTrue(ghostWork.executor().isTerminated());
    }

    @Test
    void lowLevelConstructorShouldUseTheFactoriesRegistry() throws Exception {
        Registry registry = new Registry();
        Clock clock = Clock.systemUTC();
        GhostWorkEventPublisher publisher = new GhostWorkEventPublisher();
        var executor = new io.nikitoo0os.TrackingExecutorService(
                delegate,
                new TrackingRunnableFactory(registry, clock, publisher),
                new TrackingCallableFactory(registry, clock, publisher),
                clock,
                publisher
        );

        assertEquals("done", executor.submit(() -> "done").get());
        assertEquals(1, registry.findOperations().size());
    }

    @Test
    void constructorShouldRejectFactoriesWithDifferentRegistries() {
        Registry runnableRegistry = new Registry();
        Registry callableRegistry = new Registry();
        Clock clock = Clock.systemUTC();
        GhostWorkEventPublisher publisher = new GhostWorkEventPublisher();

        assertThrows(
                IllegalArgumentException.class,
                () -> new io.nikitoo0os.TrackingExecutorService(
                        delegate,
                        new TrackingRunnableFactory(
                                runnableRegistry,
                                clock,
                                publisher
                        ),
                        new TrackingCallableFactory(
                                callableRegistry,
                                clock,
                                publisher
                        ),
                        clock,
                        publisher
                )
        );
    }

    private OperationView awaitFinishedOperation() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            List<OperationView> operations = ghostWork.operations();
            if (!operations.isEmpty()
                    && operations.getFirst().state()
                    != OperationState.RUNNING) {
                return operations.getFirst();
            }
            Thread.sleep(5);
        }
        fail("Operation did not finish in time");
        return null;
    }
}
