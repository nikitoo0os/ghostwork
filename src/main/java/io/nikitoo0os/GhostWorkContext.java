package io.nikitoo0os;

import java.util.Optional;
import java.util.UUID;

public final class GhostWorkContext {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private GhostWorkContext() {
    }

    public static CancellationToken currentCancellationToken() {
        Frame frame = CURRENT.get();
        return frame == null ? CancellationToken.none() : frame.token();
    }

    public static Optional<UUID> currentTaskId() {
        Frame frame = CURRENT.get();
        return frame == null
                ? Optional.empty()
                : Optional.of(frame.taskId());
    }

    public static Scope open(UUID taskId, CancellationToken token) {
        Frame previous = CURRENT.get();
        CURRENT.set(new Frame(
                java.util.Objects.requireNonNull(taskId),
                java.util.Objects.requireNonNull(token)
        ));
        return new Scope(previous);
    }

    private record Frame(UUID taskId, CancellationToken token) {
    }

    public static final class Scope implements AutoCloseable {
        private final Frame previous;
        private boolean closed;

        private Scope(Frame previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
