package io.nikitoo0os.wrap;

import io.nikitoo0os.entity.Task;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;

public final class WrappedCallable<T> implements Callable<T> {

    private final Callable<T> delegate;
    private final Task task;
    private final Clock clock;

    public WrappedCallable(Callable<T> delegate, Task task) {
        this(delegate, task, Clock.systemUTC());
    }
    public WrappedCallable(Callable<T> delegate, Task task, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate);
        this.task = Objects.requireNonNull(task);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public T call() throws Exception {
        task.start(Instant.now(clock));

        try {
            T result = delegate.call();
            task.complete(Instant.now(clock));
            return result;
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
