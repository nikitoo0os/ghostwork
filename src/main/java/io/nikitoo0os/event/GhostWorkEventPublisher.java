package io.nikitoo0os.event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class GhostWorkEventPublisher {

    private final List<GhostWorkEventListener> listeners =
            new CopyOnWriteArrayList<>();

    public void addListener(GhostWorkEventListener listener) {
        listeners.add(
                Objects.requireNonNull(
                        listener,
                        "Listener must not be null"
                )
        );
    }

    public void removeListener(GhostWorkEventListener listener) {
        listeners.remove(
                Objects.requireNonNull(
                        listener,
                        "Listener must not be null"
                )
        );
    }

    public void publish(GhostWorkEvent event) {
        Objects.requireNonNull(event, "Event must not be null");

        for (GhostWorkEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException ignored) {
                // Listener failures must not break GhostWork lifecycle.
            }
        }
    }
}