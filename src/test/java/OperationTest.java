import io.nikitoo0os.entity.*;
import io.nikitoo0os.entity.enums.OperationState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class OperationTest {

    @Test
    void newOperationShouldHaveRunningStateAndNullFinished() {
        Operation operation = new Operation("TestOperation");

        assertEquals(
                OperationState.RUNNING,
                operation.getState()
        );
        assertNull(operation.getFinishedAt());
    }

    @Test
    void completedOperationShouldHaveCompletedStateAndFinishedAt() {
        Operation operation = new Operation("TestOperation");
        operation.complete();

        assertEquals(OperationState.COMPLETED, operation.getState());
        assertNotNull(operation.getFinishedAt());
    }

    @Test
    void finishedOperationShouldRejectAnotherTransition() {
        Operation operation1 = new Operation("CompleteToComplete");
        operation1.complete();
        assertThrows(IllegalStateException.class, operation1::complete);

        Operation operation2 = new Operation("CompletedToTimeout");
        operation2.complete();
        assertThrows(IllegalStateException.class, operation2::timeout);
    }

    @Test
    void timeoutOperationShouldHaveTimedOutStateAndFinishedAt() {
        Operation operation = new Operation("TestOperation");
        operation.timeout();
        assertEquals(
                OperationState.TIMED_OUT,
                operation.getState()
        );

        assertNotNull(operation.getFinishedAt());
    }

    @Test
    void concurrentCompletionShouldAllowOnlyOneSuccessfulTransition() throws InterruptedException {
        Operation operation = new Operation("TestOperation");

        AtomicInteger successCount = new AtomicInteger(0);

        Thread t1 = new Thread(() -> {
            try {
                operation.complete();
                successCount.incrementAndGet();
            } catch (IllegalStateException _) {
                // Another thread completed the operation first.
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                operation.timeout();
                successCount.incrementAndGet();
            } catch (IllegalStateException _) {
                // Another thread completed the operation first.
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertEquals(1, successCount.get());
        assertNotNull(operation.getFinishedAt());
        assertTrue(
                operation.getState() == OperationState.COMPLETED
                        || operation.getState() == OperationState.TIMED_OUT
        );
    }
}
