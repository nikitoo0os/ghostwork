package io.nikitoo0os;

public enum ChildCancellationPolicy {
    NONE(false, false),
    REQUEST_CANCELLATION(false, false),
    CANCEL_QUEUED(true, false),
    INTERRUPT_RUNNING(false, true),
    CANCEL_ALL(true, true);

    private final boolean cancelQueued;
    private final boolean interruptRunning;

    ChildCancellationPolicy(
            boolean cancelQueued,
            boolean interruptRunning
    ) {
        this.cancelQueued = cancelQueued;
        this.interruptRunning = interruptRunning;
    }

    public boolean cancelQueued() {
        return cancelQueued;
    }

    public boolean interruptRunning() {
        return interruptRunning;
    }
}
