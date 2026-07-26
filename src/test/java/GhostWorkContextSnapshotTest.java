import io.nikitoo0os.CorrelationId;
import io.nikitoo0os.GhostWork;
import io.nikitoo0os.GhostWorkContext;
import io.nikitoo0os.GhostWorkContextSnapshot;
import io.nikitoo0os.OperationScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostWorkContextSnapshotTest {
    private final ExecutorService delegate =
            Executors.newSingleThreadExecutor();
    private final GhostWork ghostWork = GhostWork.create(delegate);

    @AfterEach
    void shutdown() {
        delegate.shutdownNow();
    }

    @Test
    void correlationIdShouldRejectUnsafeValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CorrelationId("line\nbreak")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CorrelationId("x".repeat(
                        CorrelationId.MAX_LENGTH + 1
                ))
        );
    }

    @Test
    void explicitCorrelationShouldPropagateIntoTaskContext()
            throws Exception {
        CorrelationId correlationId = new CorrelationId("request-42");
        AtomicReference<GhostWorkContextSnapshot> observed =
                new AtomicReference<>();
        var operation = ghostWork.startOperation(
                "Import",
                null,
                correlationId
        );

        try (OperationScope ignored = operation.openScope()) {
            ghostWork.executor().submit(
                    "Fetch",
                    () -> observed.set(
                            GhostWorkContext.capture().orElseThrow()
                    )
            ).get(1, TimeUnit.SECONDS);
        }
        operation.complete();

        assertEquals(correlationId, observed.get().correlationId());
        assertEquals(operation.id(), observed.get().operationId());
        assertTrue(observed.get().taskId() != null);
    }

    @Test
    void nestedScopeShouldRestorePreviousSnapshot() {
        GhostWorkContextSnapshot parent =
                GhostWorkContextSnapshot.operation(
                        UUID.randomUUID(),
                        new CorrelationId("parent")
                );
        GhostWorkContextSnapshot child =
                GhostWorkContextSnapshot.operation(
                        UUID.randomUUID(),
                        new CorrelationId("child")
                );

        try (GhostWorkContext.Scope ignored =
                     GhostWorkContext.open(parent)) {
            try (GhostWorkContext.Scope nested =
                         GhostWorkContext.open(child)) {
                assertEquals(
                        child,
                        GhostWorkContext.capture().orElseThrow()
                );
            }
            assertEquals(parent, GhostWorkContext.capture().orElseThrow());
        }

        assertTrue(GhostWorkContext.capture().isEmpty());
    }

    @Test
    void workerThreadShouldBeCleanAfterTrackedExecution()
            throws Exception {
        var operation = ghostWork.startOperation("Clean worker", null);
        try (OperationScope ignored = operation.openScope()) {
            ghostWork.executor().submit("Tracked", () -> {
            }).get(1, TimeUnit.SECONDS);
        }
        operation.complete();

        Optional<GhostWorkContextSnapshot> remaining = delegate.submit(
                GhostWorkContext::capture
        ).get(1, TimeUnit.SECONDS);

        assertFalse(remaining.isPresent());
    }
}
