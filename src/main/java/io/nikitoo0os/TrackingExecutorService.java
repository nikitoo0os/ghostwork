package io.nikitoo0os;

import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.factory.TrackingCallableFactory;
import io.nikitoo0os.factory.TrackingRunnableFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public final class TrackingExecutorService {
    private final ExecutorService delegate;
    private final TrackingRunnableFactory runnableFactory;
    private final TrackingCallableFactory callableFactory;

    public TrackingExecutorService(ExecutorService delegate, TrackingRunnableFactory runnableFactory, TrackingCallableFactory callableFactory){
        this.delegate = delegate;
        this.runnableFactory = runnableFactory;
        this.callableFactory = callableFactory;
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

    public <T> Future<T> submit(
            Operation operation,
            String taskName,
            Callable<T> callable
    ) {
        Callable<T> trackingCallable =
                callableFactory.wrap(operation, taskName, callable);

        return delegate.submit(trackingCallable);
    }
}
