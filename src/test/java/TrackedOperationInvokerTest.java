import io.nikitoo0os.GhostWork;
import io.nikitoo0os.OperationView;
import io.nikitoo0os.annotation.TrackedOperation;
import io.nikitoo0os.annotation.TrackedOperationInvoker;
import io.nikitoo0os.annotation.TrackedOperationResolver;
import io.nikitoo0os.entity.enums.OperationState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    }
}