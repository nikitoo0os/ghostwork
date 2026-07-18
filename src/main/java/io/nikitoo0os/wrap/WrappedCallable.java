package io.nikitoo0os.wrap;

import io.nikitoo0os.entity.Task;

import java.util.Objects;
import java.util.concurrent.Callable;

public final class WrappedCallable<T> implements Callable<T> {

    private final Callable<T> delegate;
    private final Task task;

    public WrappedCallable(Callable<T> delegate, Task task) {
        this.delegate = Objects.requireNonNull(delegate);
        this.task = Objects.requireNonNull(task);
    }

    @Override
    public T call() throws Exception {
        task.start();

        try {
            T result = delegate.call();
            task.complete();
            return result;
        } catch (Throwable original) {
            try {
                task.fail();
            } catch (Throwable stateFailure) {
                original.addSuppressed(stateFailure);
            }

            throw original;
        }
    }
}
