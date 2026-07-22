import io.nikitoo0os.GhostWork;
import io.nikitoo0os.OperationView;
import io.nikitoo0os.TaskView;
import io.nikitoo0os.annotation.TrackedOperation;
import io.nikitoo0os.annotation.TrackedOperationInvoker;
import io.nikitoo0os.annotation.TrackedOperationResolver;
import io.nikitoo0os.entity.enums.OperationState;
import io.nikitoo0os.entity.enums.TaskState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class TrackedOperationInvokerTest {

    private final ExecutorService executor =
            Executors.newFixedThreadPool(2);

    private final GhostWork ghostWork =
            GhostWork.create(executor);

    private final TrackedOperationInvoker invoker =
            new TrackedOperationInvoker(
                    ghostWork,
                    new TrackedOperationResolver()
            );

    @AfterEach
    void tearDown() {
        ghostWork.executor().shutdownNow();
    }

    @Test
    void callShouldRunThroughGhostWorkWhenMethodAnnotated()
            throws Exception {
        Method method = TestService.class.getDeclaredMethod(
                "importUsers"
        );

        String result = invoker.call(
                method,
                () -> "done"
        );

        assertEquals("done", result);

        OperationView operation =
                findOperationByName("ImportUsers");

        assertEquals(OperationState.COMPLETED, operation.state());
    }

    @Test
    void runShouldRunThroughGhostWorkWhenMethodAnnotated()
            throws Exception {
        Method method = TestService.class.getDeclaredMethod(
                "cleanup"
        );

        invoker.run(
                method,
                () -> {
                }
        );

        OperationView operation =
                findOperationByName("Cleanup");

        assertEquals(OperationState.COMPLETED, operation.state());
    }

    @Test
    void callShouldThrowWhenMethodNotAnnotated()
            throws NoSuchMethodException {
        Method method = TestService.class.getDeclaredMethod(
                "notAnnotated"
        );

        assertThrows(
                IllegalStateException.class,
                () -> invoker.call(
                        method,
                        () -> "done"
                )
        );
    }

    @Test
    void constructorShouldRejectNullGhostWork() {
        assertThrows(
                NullPointerException.class,
                () -> new TrackedOperationInvoker(
                        null,
                        new TrackedOperationResolver()
                )
        );
    }

    @Test
    void constructorShouldRejectNullResolver() {
        assertThrows(
                NullPointerException.class,
                () -> new TrackedOperationInvoker(
                        ghostWork,
                        null
                )
        );
    }

    @Test
    void runShouldTrackSubmittedTaskUnderAnnotatedOperation()
            throws Exception {
        Method method = TestService.class.getDeclaredMethod(
                "importUsers"
        );

        invoker.run(
                method,
                () -> {
                    try {
                        Future<?> future = ghostWork.executor()
                                .submit(
                                        "ReadFile",
                                        () -> {
                                        }
                                );

                        future.get(1, TimeUnit.SECONDS);
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                }
        );

        OperationView operation =
                findOperationByName("ImportUsers");

        List<TaskView> tasks =
                ghostWork.tasks(operation.id());

        assertEquals(OperationState.COMPLETED, operation.state());
        assertEquals(1, tasks.size());
        assertEquals("ReadFile", tasks.getFirst().name());
        assertEquals(TaskState.COMPLETED, tasks.getFirst().state());

        assertTrue(
                ghostWork.operations()
                        .stream()
                        .noneMatch(candidate -> candidate.name().equals("Implicit:ReadFile"))
        );
    }

    @Test
    void invokeShouldCallAnnotatedMethodAndTrackOperation()
            throws Exception {
        TestService service = new TestService();

        Method method = TestService.class.getDeclaredMethod(
                "returnValue"
        );

        Object result = invoker.invoke(
                service,
                method,
                null
        );

        assertEquals("value", result);

        OperationView operation =
                findOperationByName("ReturnValue");

        assertEquals(OperationState.COMPLETED, operation.state());
    }

    @Test
    void invokeShouldPassArgumentsToAnnotatedMethod()
            throws Exception {
        TestService service = new TestService();

        Method method = TestService.class.getDeclaredMethod(
                "greet",
                String.class
        );

        Object result = invoker.invoke(
                service,
                method,
                new Object[]{"Nikita"}
        );

        assertEquals("Hello, Nikita", result);

        OperationView operation =
                findOperationByName("Greeting");

        assertEquals(OperationState.COMPLETED, operation.state());
    }

    @Test
    void invokeShouldUnwrapOriginalExceptionAndFailOperation()
            throws Exception {
        TestService service = new TestService();

        Method method = TestService.class.getDeclaredMethod(
                "fail"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> invoker.invoke(
                        service,
                        method,
                        null
                )
        );

        assertEquals("bad input", exception.getMessage());

        OperationView operation =
                findOperationByName("FailingMethod");

        assertEquals(OperationState.FAILED, operation.state());
    }

    private OperationView findOperationByName(String name) {
        return ghostWork.operations()
                .stream()
                .filter(operation -> operation.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static final class TestService {

        @TrackedOperation("ImportUsers")
        void importUsers() {
        }

        @TrackedOperation("Cleanup")
        void cleanup() {
        }

        void notAnnotated() {
        }

        @TrackedOperation("ReturnValue")
        String returnValue() {
            return "value";
        }

        @TrackedOperation("Greeting")
        String greet(String name) {
            return "Hello, " + name;
        }

        @TrackedOperation("FailingMethod")
        String fail() {
            throw new IllegalArgumentException("bad input");
        }
    }
}