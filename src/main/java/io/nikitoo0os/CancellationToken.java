package io.nikitoo0os;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CancellationException;

public interface CancellationToken {
    boolean isCancellationRequested();

    Optional<CancellationCause> cause();

    Optional<Instant> requestedAt();

    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new CancellationException(
                    "Cancellation requested: "
                            + cause().orElse(CancellationCause.UNKNOWN)
            );
        }
    }

    CancellationRegistration onCancellation(Runnable callback);

    static CancellationToken none() {
        return NonCancellableToken.INSTANCE;
    }

    enum NonCancellableToken implements CancellationToken {
        INSTANCE;

        @Override
        public boolean isCancellationRequested() {
            return false;
        }

        @Override
        public Optional<CancellationCause> cause() {
            return Optional.empty();
        }

        @Override
        public Optional<Instant> requestedAt() {
            return Optional.empty();
        }

        @Override
        public CancellationRegistration onCancellation(Runnable callback) {
            java.util.Objects.requireNonNull(callback, "Callback must not be null");
            return () -> {
            };
        }
    }
}
