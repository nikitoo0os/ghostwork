package io.nikitoo0os.entity;

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
            task.complete();
        } catch (RuntimeException e) {
            task.fail();
            throw e;
        }
    }
}
