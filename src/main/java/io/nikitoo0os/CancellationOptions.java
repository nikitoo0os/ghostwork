package io.nikitoo0os;

import java.util.Objects;

public record CancellationOptions(
        CancellationCause cause,
        boolean cancelQueued,
        boolean interruptRunning
) {
    public CancellationOptions {
        Objects.requireNonNull(cause, "Cancellation cause must not be null");
    }

    public static CancellationOptions requestOnly(CancellationCause cause) {
        return new CancellationOptions(cause, false, false);
    }

    public static CancellationOptions cancelAll(CancellationCause cause) {
        return new CancellationOptions(cause, true, true);
    }
}
