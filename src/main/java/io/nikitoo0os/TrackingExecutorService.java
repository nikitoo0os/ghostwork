package io.nikitoo0os;

import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.factory.TrackingRunnableFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public final class TrackingExecutorService {
    private final ExecutorService delegate;
    private final TrackingRunnableFactory runnableFactory;

    public TrackingExecutorService(ExecutorService delegate, TrackingRunnableFactory runnableFactory){
        this.delegate = delegate;
        this.runnableFactory = runnableFactory;
    }

    public Future<?> submit(
            Operation operation,
            String taskName,
            Runnable runnable
    ) {
        Runnable trackingRunnable =
                runnableFactory.wrap(operation, taskName, runnable);

        return delegate.submit(trackingRunnable);
    }
}
