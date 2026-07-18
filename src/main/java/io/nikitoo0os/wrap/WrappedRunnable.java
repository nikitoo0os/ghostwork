package io.nikitoo0os.wrap;

import io.nikitoo0os.entity.Task;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class WrappedRunnable implements Runnable {

    private final Runnable delegate;
    private final Task task;
    private final Clock clock;

    public WrappedRunnable(Runnable delegate, Task task) {
        this(delegate, task, Clock.systemUTC());
    }

    public WrappedRunnable(Runnable delegate, Task task, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate);
        this.task = Objects.requireNonNull(task);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void run() {
        task.start(Instant.now(clock));
        try {
            delegate.run();
            task.complete(Instant.now(clock));
        } catch (Throwable original) {
            try {
                task.fail(Instant.now(clock));
            } catch (Throwable stateFailure) {
                original.addSuppressed(stateFailure);
            }

            throw original;
        }
    }
}
