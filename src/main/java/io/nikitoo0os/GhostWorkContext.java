package io.nikitoo0os;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class GhostWorkContext {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private GhostWorkContext() {
    }

    public static CancellationToken currentCancellationToken() {
        Frame frame = CURRENT.get();
        return frame == null
                ? CancellationToken.none()
                : frame.snapshot().cancellationToken();
    }

    public static Optional<UUID> currentTaskId() {
        Frame frame = CURRENT.get();
        return frame == null
                ? Optional.empty()
                : Optional.ofNullable(frame.snapshot().taskId());
    }

    public static Optional<CorrelationId> currentCorrelationId() {
        return capture().map(GhostWorkContextSnapshot::correlationId);
    }

    public static Optional<GhostWorkContextSnapshot> capture() {
        Frame frame = CURRENT.get();
        return frame == null
                ? Optional.empty()
                : Optional.of(frame.snapshot());
    }

    public static Scope open(GhostWorkContextSnapshot snapshot) {
        Frame previous = CURRENT.get();
        CURRENT.set(new Frame(Objects.requireNonNull(snapshot)));
        return new Scope(previous);
    }

    public static Scope openOperation(
            UUID operationId,
            CorrelationId correlationId
    ) {
        return open(GhostWorkContextSnapshot.operation(
                operationId,
                correlationId
        ));
    }

    public static Scope openTask(
            UUID taskId,
            CancellationToken token,
            GhostWorkContextSnapshot submissionContext,
            boolean detached
    ) {
        GhostWorkContextSnapshot parent = Objects.requireNonNull(
                submissionContext,
                "Submission context must not be null"
        );
        return open(parent.withTask(taskId, token, detached));
    }

    public static Scope openSchedule(
            UUID scheduleId,
            long executionNumber
    ) {
        GhostWorkContextSnapshot current = capture().orElseThrow(() ->
                new IllegalStateException("GhostWork context is empty")
        );
        return open(current.withSchedule(scheduleId, executionNumber));
    }

    public static Scope open(UUID taskId, CancellationToken token) {
        GhostWorkContextSnapshot current = capture().orElseGet(() ->
                GhostWorkContextSnapshot.operation(
                        UUID.randomUUID(),
                        CorrelationId.random()
                )
        );
        return openTask(taskId, token, current, false);
    }

    private record Frame(GhostWorkContextSnapshot snapshot) {
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
