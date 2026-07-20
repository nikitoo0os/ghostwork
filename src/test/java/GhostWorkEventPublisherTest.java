import io.nikitoo0os.OperationView;
import io.nikitoo0os.event.GhostWorkEvent;
import io.nikitoo0os.event.GhostWorkEventListener;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.event.GhostWorkEventType;
import io.nikitoo0os.entity.enums.OperationState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class GhostWorkEventPublisherTest {

    @Test
    void publishShouldNotifyRegisteredListener() {
        GhostWorkEventPublisher publisher =
                new GhostWorkEventPublisher();

        AtomicReference<GhostWorkEvent> receivedEvent =
                new AtomicReference<>();

        GhostWorkEvent event = operationEvent();

        publisher.addListener(receivedEvent::set);

        publisher.publish(event);

        assertSame(event, receivedEvent.get());
    }

    @Test
    void removedListenerShouldNotReceiveEvents() {
        GhostWorkEventPublisher publisher =
                new GhostWorkEventPublisher();

        AtomicReference<GhostWorkEvent> receivedEvent =
                new AtomicReference<>();

        GhostWorkEventListener listener = receivedEvent::set;

        publisher.addListener(listener);
        publisher.removeListener(listener);

        publisher.publish(operationEvent());

        assertNull(receivedEvent.get());
    }

    @Test
    void publishShouldNotifyMultipleListenersInOrder() {
        GhostWorkEventPublisher publisher =
                new GhostWorkEventPublisher();

        StringBuilder calls = new StringBuilder();

        publisher.addListener(event -> calls.append("A"));
        publisher.addListener(event -> calls.append("B"));

        publisher.publish(operationEvent());

        assertEquals("AB", calls.toString());
    }

    @Test
    void addListenerShouldRejectNull() {
        GhostWorkEventPublisher publisher =
                new GhostWorkEventPublisher();

        assertThrows(
                NullPointerException.class,
                () -> publisher.addListener(null)
        );
    }

    @Test
    void removeListenerShouldRejectNull() {
        GhostWorkEventPublisher publisher =
                new GhostWorkEventPublisher();

        assertThrows(
                NullPointerException.class,
                () -> publisher.removeListener(null)
        );
    }

    @Test
    void publishShouldRejectNullEvent() {
        GhostWorkEventPublisher publisher =
                new GhostWorkEventPublisher();

        assertThrows(
                NullPointerException.class,
                () -> publisher.publish(null)
        );
    }

    @Test
    void eventShouldRejectNullType() {
        assertThrows(
                NullPointerException.class,
                () -> new GhostWorkEvent(
                        null,
                        operationView(),
                        null,
                        null
                )
        );
    }

    @Test
    void eventShouldRejectNullOperation() {
        assertThrows(
                NullPointerException.class,
                () -> new GhostWorkEvent(
                        GhostWorkEventType.OPERATION_COMPLETED,
                        null,
                        null,
                        null
                )
        );
    }

    private GhostWorkEvent operationEvent() {
        return new GhostWorkEvent(
                GhostWorkEventType.OPERATION_COMPLETED,
                operationView(),
                null,
                null
        );
    }

    private OperationView operationView() {
        return new OperationView(
                UUID.randomUUID(),
                "Operation",
                OperationState.COMPLETED,
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:01Z")
        );
    }

    @Test
    void listenerFailureShouldNotPreventOtherListenersFromReceivingEvent() {
        GhostWorkEventPublisher publisher =
                new GhostWorkEventPublisher();

        AtomicReference<GhostWorkEvent> receivedEvent =
                new AtomicReference<>();

        GhostWorkEvent event = operationEvent();

        publisher.addListener(ignored -> {
            throw new RuntimeException("listener failed");
        });
        publisher.addListener(receivedEvent::set);

        publisher.publish(event);

        assertSame(event, receivedEvent.get());
    }
}