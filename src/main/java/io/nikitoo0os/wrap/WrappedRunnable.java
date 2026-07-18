package io.nikitoo0os.wrap;

import io.nikitoo0os.entity.Task;

import java.util.Objects;

public final class WrappedRunnable implements Runnable {

    private final Runnable delegate;
    private final Task task;

    public WrappedRunnable(Runnable delegate, Task task) {
        this.delegate = Objects.requireNonNull(delegate);
        this.task = Objects.requireNonNull(task);
    }

    @Override
    public void run() {
        task.start();
        try {
            delegate.run();

        } catch (Throwable original) {
            try {
                task.fail();
            } catch (Throwable stateFailure) {
                original.addSuppressed(stateFailure);
            }

            throw original;
        }

        task.complete();
    }
}
