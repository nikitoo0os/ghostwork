import io.nikitoo0os.context.OperationContext;
import io.nikitoo0os.entity.Operation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class OperationContextTest {

    @AfterEach
    void tearDown() {
        OperationContext.clear();
    }

    @Test
    void currentShouldReturnEmptyWhenNoOperationSet() {
        assertTrue(OperationContext.current().isEmpty());
    }

    @Test
    void setShouldExposeCurrentOperation() {
        Operation operation = new Operation("TestOperation");

        OperationContext.set(operation);

        assertEquals(Optional.of(operation), OperationContext.current());
    }

    @Test
    void clearShouldRemoveCurrentOperation() {
        Operation operation = new Operation("TestOperation");

        OperationContext.set(operation);
        OperationContext.clear();

        assertTrue(OperationContext.current().isEmpty());
    }

    @Test
    void setShouldRejectNullOperation() {
        assertThrows(
                NullPointerException.class,
                () -> OperationContext.set(null)
        );
    }

    @Test
    void operationContextShouldBeThreadLocal()
            throws InterruptedException {
        Operation mainThreadOperation =
                new Operation("MainThreadOperation");

        Operation workerThreadOperation =
                new Operation("WorkerThreadOperation");

        OperationContext.set(mainThreadOperation);

        AtomicReference<Optional<Operation>> workerBeforeSet =
                new AtomicReference<>();

        AtomicReference<Optional<Operation>> workerAfterSet =
                new AtomicReference<>();

        Thread worker = new Thread(() -> {
            workerBeforeSet.set(OperationContext.current());

            OperationContext.set(workerThreadOperation);
            workerAfterSet.set(OperationContext.current());

            OperationContext.clear();
        });

        worker.start();
        worker.join();

        assertTrue(workerBeforeSet.get().isEmpty());
        assertEquals(
                Optional.of(workerThreadOperation),
                workerAfterSet.get()
        );

        assertEquals(
                Optional.of(mainThreadOperation),
                OperationContext.current()
        );
    }

    @Test
    void openShouldExposeOperationUntilScopeIsClosed() {
        Operation operation = new Operation("TestOperation");

        try (OperationContext.Scope ignored = OperationContext.open(operation)) {
            assertEquals(
                    Optional.of(operation),
                    OperationContext.current()
            );
        }

        assertTrue(OperationContext.current().isEmpty());
    }

    @Test
    void openShouldRestorePreviousOperationWhenScopeIsClosed() {
        Operation outerOperation = new Operation("OuterOperation");
        Operation innerOperation = new Operation("InnerOperation");

        OperationContext.set(outerOperation);

        try (OperationContext.Scope ignored =
                     OperationContext.open(innerOperation)) {
            assertEquals(
                    Optional.of(innerOperation),
                    OperationContext.current()
            );
        }

        assertEquals(
                Optional.of(outerOperation),
                OperationContext.current()
        );
    }
}