package io.nikitoo0os.scheduling;

import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ExternalTrackedScheduledFuture<V> implements ScheduledFuture<V> {
    private final ScheduledFuture<V> delegate;
    private final ScheduleDefinition definition;
    private final SchedulingCoordinator coordinator;

    ExternalTrackedScheduledFuture(
            ScheduledFuture<V> delegate,
            ScheduleDefinition definition,
            SchedulingCoordinator coordinator
    ) {
        this.delegate = delegate;
        this.definition = definition;
        this.coordinator = coordinator;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return delegate.getDelay(unit);
    }

    @Override
    public int compareTo(Delayed other) {
        Delayed candidate = other instanceof ExternalTrackedScheduledFuture<?> tracked
                ? tracked.delegate
                : other;
        return delegate.compareTo(candidate);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean accepted = delegate.cancel(mayInterruptIfRunning);
        if (accepted) {
            coordinator.cancel(definition, mayInterruptIfRunning);
        }
        return accepted;
    }

    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
        return delegate.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return delegate.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.get(timeout, unit);
    }
}
