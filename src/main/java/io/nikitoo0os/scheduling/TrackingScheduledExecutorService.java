package io.nikitoo0os.scheduling;

import io.nikitoo0os.ExecutorMetadata;
import io.nikitoo0os.GhostWork;
import io.nikitoo0os.TrackingExecutorService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class TrackingScheduledExecutorService
        implements ScheduledExecutorService {
    private final ScheduledExecutorService delegate;
    private final TrackingExecutorService executor;
    private final SchedulingCoordinator coordinator;
    private final Clock clock;
    private final java.util.Set<ScheduleDefinition> definitions =
            ConcurrentHashMap.newKeySet();
    private final AtomicReference<SchedulerShutdownView> shutdown =
            new AtomicReference<>();

    public TrackingScheduledExecutorService(
            GhostWork ghostWork,
            ScheduledExecutorService delegate,
            ScheduleRegistry registry,
            Clock clock,
            ExecutorMetadata metadata
    ) {
        this.delegate = Objects.requireNonNull(
                delegate,
                "Delegate scheduler must not be null"
        );
        this.executor = Objects.requireNonNull(ghostWork).decorate(
                delegate,
                Objects.requireNonNull(metadata)
        );
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.coordinator = new SchedulingCoordinator(
                ghostWork,
                Objects.requireNonNull(registry),
                clock
        );
    }

    public ScheduledFuture<?> schedule(
            ScheduleOptions options,
            Runnable command,
            long delay,
            TimeUnit unit
    ) {
        Objects.requireNonNull(command, "Command must not be null");
        Objects.requireNonNull(unit, "Time unit must not be null");
        Instant expectedAt = plus(clock.instant(), delay, unit);
        ScheduleDefinition definition = coordinator.create(
                options,
                ScheduleType.ONE_TIME,
                new ScheduleTrigger.OneTime(expectedAt)
        );
        definitions.add(definition);
        coordinator.expect(definition, expectedAt);
        SubmissionGate gate = new SubmissionGate();
        try {
            ScheduledFuture<?> future = delegate.schedule(
                    () -> {
                        gate.awaitOpen();
                        coordinator.run(definition, options, command);
                    },
                    delay,
                    unit
            );
            coordinator.activate(definition, future);
            TrackedScheduledFuture<?> tracked = new TrackedScheduledFuture<>(
                    future,
                    definition,
                    coordinator
            );
            gate.open();
            return tracked;
        } catch (RuntimeException failure) {
            coordinator.registrationFailed(definition);
            gate.open();
            throw failure;
        }
    }

    public <V> ScheduledFuture<V> schedule(
            ScheduleOptions options,
            Callable<V> callable,
            long delay,
            TimeUnit unit
    ) {
        Objects.requireNonNull(callable, "Callable must not be null");
        Objects.requireNonNull(unit, "Time unit must not be null");
        Instant expectedAt = plus(clock.instant(), delay, unit);
        ScheduleDefinition definition = coordinator.create(
                options,
                ScheduleType.ONE_TIME,
                new ScheduleTrigger.OneTime(expectedAt)
        );
        definitions.add(definition);
        coordinator.expect(definition, expectedAt);
        SubmissionGate gate = new SubmissionGate();
        try {
            ScheduledFuture<V> future = delegate.schedule(
                    () -> {
                        gate.awaitOpen();
                        return coordinator.call(
                                definition,
                                options,
                                callable
                        );
                    },
                    delay,
                    unit
            );
            coordinator.activate(definition, future);
            TrackedScheduledFuture<V> tracked = new TrackedScheduledFuture<>(
                    future,
                    definition,
                    coordinator
            );
            gate.open();
            return tracked;
        } catch (RuntimeException failure) {
            coordinator.registrationFailed(definition);
            gate.open();
            throw failure;
        }
    }

    public ScheduledFuture<?> scheduleAtFixedRate(
            ScheduleOptions options,
            Runnable command,
            long initialDelay,
            long period,
            TimeUnit unit
    ) {
        Objects.requireNonNull(command, "Command must not be null");
        Objects.requireNonNull(unit, "Time unit must not be null");
        Instant firstAt = plus(clock.instant(), initialDelay, unit);
        Duration periodDuration = positiveDuration(period, unit, "Period");
        ScheduleDefinition definition = coordinator.create(
                options,
                ScheduleType.FIXED_RATE,
                new ScheduleTrigger.FixedRate(firstAt, periodDuration)
        );
        definitions.add(definition);
        coordinator.expect(definition, firstAt);
        SubmissionGate gate = new SubmissionGate();
        try {
            ScheduledFuture<?> future = delegate.scheduleAtFixedRate(
                    () -> {
                        gate.awaitOpen();
                        coordinator.run(definition, options, command);
                    },
                    initialDelay,
                    period,
                    unit
            );
            coordinator.activate(definition, future);
            TrackedScheduledFuture<?> tracked = new TrackedScheduledFuture<>(
                    future,
                    definition,
                    coordinator
            );
            gate.open();
            return tracked;
        } catch (RuntimeException failure) {
            coordinator.registrationFailed(definition);
            gate.open();
            throw failure;
        }
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(
            ScheduleOptions options,
            Runnable command,
            long initialDelay,
            long delay,
            TimeUnit unit
    ) {
        Objects.requireNonNull(command, "Command must not be null");
        Objects.requireNonNull(unit, "Time unit must not be null");
        Instant firstAt = plus(clock.instant(), initialDelay, unit);
        Duration delayDuration = positiveDuration(delay, unit, "Delay");
        ScheduleDefinition definition = coordinator.create(
                options,
                ScheduleType.FIXED_DELAY,
                new ScheduleTrigger.FixedDelay(firstAt, delayDuration)
        );
        definitions.add(definition);
        coordinator.expect(definition, firstAt);
        SubmissionGate gate = new SubmissionGate();
        try {
            ScheduledFuture<?> future = delegate.scheduleWithFixedDelay(
                    () -> {
                        gate.awaitOpen();
                        coordinator.run(definition, options, command);
                    },
                    initialDelay,
                    delay,
                    unit
            );
            coordinator.activate(definition, future);
            TrackedScheduledFuture<?> tracked = new TrackedScheduledFuture<>(
                    future,
                    definition,
                    coordinator
            );
            gate.open();
            return tracked;
        } catch (RuntimeException failure) {
            coordinator.registrationFailed(definition);
            gate.open();
            throw failure;
        }
    }

    @Override
    public ScheduledFuture<?> schedule(
            Runnable command,
            long delay,
            TimeUnit unit
    ) {
        return schedule(defaultOptions(command), command, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(
            Callable<V> callable,
            long delay,
            TimeUnit unit
    ) {
        return schedule(defaultOptions(callable), callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
            Runnable command,
            long initialDelay,
            long period,
            TimeUnit unit
    ) {
        return scheduleAtFixedRate(
                defaultOptions(command),
                command,
                initialDelay,
                period,
                unit
        );
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
            Runnable command,
            long initialDelay,
            long delay,
            TimeUnit unit
    ) {
        return scheduleWithFixedDelay(
                defaultOptions(command),
                command,
                initialDelay,
                delay,
                unit
        );
    }

    @Override
    public void shutdown() {
        executor.shutdown();
        recordShutdown(SchedulerShutdownMethod.SHUTDOWN, 0);
        reconcileCancelledFutures();
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> queued = executor.shutdownNow();
        recordShutdown(
                SchedulerShutdownMethod.SHUTDOWN_NOW,
                queued.size()
        );
        reconcileCancelledFutures();
        return queued;
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        boolean terminated = delegate.awaitTermination(timeout, unit);
        if (terminated) {
            shutdown.updateAndGet(current -> current == null
                    ? null
                    : new SchedulerShutdownView(
                            current.method(),
                            current.requestedAt(),
                            current.activeExecutions(),
                            current.expectedExecutions(),
                            current.returnedQueuedTasks(),
                            true,
                            clock.instant()
                    ));
        }
        reconcileCancelledFutures();
        return terminated;
    }

    public java.util.Optional<SchedulerShutdownView> shutdownDiagnostics() {
        return java.util.Optional.ofNullable(shutdown.get());
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return executor.submit(task, result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks
    ) throws InterruptedException {
        return executor.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks,
            long timeout,
            TimeUnit unit
    ) throws InterruptedException {
        return executor.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return executor.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(
            Collection<? extends Callable<T>> tasks,
            long timeout,
            TimeUnit unit
    ) throws InterruptedException, ExecutionException, TimeoutException {
        return executor.invokeAny(tasks, timeout, unit);
    }

    private static ScheduleOptions defaultOptions(Object task) {
        String simpleName = task.getClass().getSimpleName();
        String name = simpleName.isBlank() ? "ScheduledTask" : simpleName;
        return ScheduleOptions.manual(name);
    }

    private void recordShutdown(
            SchedulerShutdownMethod method,
            int returnedTasks
    ) {
        shutdown.compareAndSet(
                null,
                new SchedulerShutdownView(
                        method,
                        clock.instant(),
                        definitions.stream()
                                .mapToLong(ScheduleDefinition::activeExecutionCount)
                                .sum(),
                        definitions.stream()
                                .mapToLong(ScheduleDefinition::expectedExecutionCount)
                                .sum(),
                        returnedTasks,
                        delegate.isTerminated(),
                        delegate.isTerminated() ? clock.instant() : null
                )
        );
    }

    private void reconcileCancelledFutures() {
        definitions.forEach(coordinator::reconcileDelegateCancellation);
    }

    private static Instant plus(Instant base, long delay, TimeUnit unit) {
        try {
            return base.plusNanos(unit.toNanos(delay));
        } catch (RuntimeException overflow) {
            return delay < 0 ? Instant.MIN : Instant.MAX;
        }
    }

    private static Duration positiveDuration(
            long value,
            TimeUnit unit,
            String name
    ) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        long nanos = unit.toNanos(value);
        if (nanos <= 0) {
            return Duration.ofNanos(1);
        }
        return Duration.ofNanos(nanos);
    }

    private static final class SubmissionGate {
        private final CountDownLatch latch = new CountDownLatch(1);

        void open() {
            latch.countDown();
        }

        void awaitOpen() {
            boolean interrupted = false;
            while (true) {
                try {
                    latch.await();
                    break;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
