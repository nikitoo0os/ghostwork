import io.nikitoo0os.GhostWork;
import io.nikitoo0os.event.ErrorInfo;
import io.nikitoo0os.event.GhostWorkEventType;
import io.nikitoo0os.event.GhostWorkLifecycleEvent;
import io.nikitoo0os.event.OperationLifecycleEvent;
import io.nikitoo0os.event.TaskLifecycleEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredLifecycleEventTest {
    private final ExecutorService delegate =
            Executors.newSingleThreadExecutor();
    private final GhostWork ghostWork = GhostWork.create(delegate);

    @AfterEach
    void shutdown() {
        delegate.shutdownNow();
    }

    @Test
    void shouldPublishOrderedOperationAndTaskLifecycle() throws Exception {
        List<GhostWorkLifecycleEvent> events = new ArrayList<>();
        ghostWork.addLifecycleEventListener(events::add);

        ghostWork.call("Import", () -> {
            ghostWork.executor().submit("Fetch", () -> {
            }).get(1, TimeUnit.SECONDS);
            return null;
        });

        assertEquals(
                List.of(
                        GhostWorkEventType.OPERATION_STARTED,
                        GhostWorkEventType.TASK_CREATED,
                        GhostWorkEventType.TASK_SUBMITTED,
                        GhostWorkEventType.TASK_STARTED,
                        GhostWorkEventType.TASK_COMPLETED,
                        GhostWorkEventType.OPERATION_COMPLETED
                ),
                events.stream().map(StructuredLifecycleEventTest::type)
                        .toList()
        );
        assertEquals(
                List.of(1L, 2L, 3L, 4L, 5L, 6L),
                events.stream()
                        .map(GhostWorkLifecycleEvent::sequence)
                        .toList()
        );
        assertTrue(events.stream().allMatch(event ->
                event.correlationId().equals(events.getFirst().correlationId())
        ));
    }

    @Test
    void failureEventShouldExposeBoundedMetadataInsteadOfThrowable() {
        List<GhostWorkLifecycleEvent> events = new ArrayList<>();
        ghostWork.addLifecycleEventListener(events::add);

        try {
            ghostWork.run(
                    "Failure",
                    () -> {
                        throw new IllegalStateException(
                                "x".repeat(ErrorInfo.MAX_MESSAGE_LENGTH + 10)
                        );
                    }
            );
        } catch (IllegalStateException ignored) {
        }

        OperationLifecycleEvent failed = events.stream()
                .filter(OperationLifecycleEvent.class::isInstance)
                .map(OperationLifecycleEvent.class::cast)
                .filter(event -> event.type()
                        == GhostWorkEventType.OPERATION_FAILED)
                .findFirst()
                .orElseThrow();
        assertEquals(
                IllegalStateException.class.getName(),
                failed.error().type()
        );
        assertEquals(
                ErrorInfo.MAX_MESSAGE_LENGTH,
                failed.error().message().length()
        );
        assertNull(failed.cancellationCause());
    }

    private static GhostWorkEventType type(
            GhostWorkLifecycleEvent event
    ) {
        if (event instanceof OperationLifecycleEvent operation) {
            return operation.type();
        }
        return ((TaskLifecycleEvent) event).type();
    }
}
