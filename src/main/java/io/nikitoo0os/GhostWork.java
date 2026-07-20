package io.nikitoo0os;

import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.event.GhostWorkEventListener;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.factory.TrackingCallableFactory;
import io.nikitoo0os.factory.TrackingRunnableFactory;
import io.nikitoo0os.operation.OperationDefinition;
import io.nikitoo0os.runner.OperationRunner;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public final class GhostWork {
    private final Registry registry;
    private final OperationRunner operationRunner;
    private final TrackingExecutorService executor;
    private final GhostWorkEventPublisher eventPublisher;

    private final Detector detector;

    private GhostWork(Registry registry, OperationRunner operationRunner, TrackingExecutorService executor, Detector detector, GhostWorkEventPublisher eventPublisher) {
        this.registry = Objects.requireNonNull(registry, "Registry must not be null");
        this.operationRunner = Objects.requireNonNull(operationRunner, "Operation runner must not be null");
        this.executor = Objects.requireNonNull(executor, "Executor must not be null");
        this.detector = Objects.requireNonNull(detector, "Detector must not be null");
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher,
                "Event publisher must not be null"
        );
    }

    public static GhostWork create(ExecutorService delegate) {
        Objects.requireNonNull(delegate, "Delegate executor must not be null");
        Registry registry = new Registry();
        Clock clock = Clock.systemUTC();
        Detector detector = new Detector(registry, clock);

        GhostWorkEventPublisher eventPublisher =
                new GhostWorkEventPublisher();

        TrackingRunnableFactory runnableFactory =
                new TrackingRunnableFactory(registry, clock, eventPublisher);

        TrackingCallableFactory callableFactory =
                new TrackingCallableFactory(registry, clock, eventPublisher);

        TrackingExecutorService executor =
                new TrackingExecutorService(
                        delegate,
                        runnableFactory,
                        callableFactory,
                        clock,
                        eventPublisher
                );


        OperationRunner operationRunner =
                new OperationRunner(registry, eventPublisher);

        return new GhostWork(registry, operationRunner, executor, detector, eventPublisher);
    }

    public void run(String operationName, Runnable runnable) {
        operationRunner.run(operationName, runnable);
    }

    public void run(OperationDefinition definition, Runnable runnable) {
        operationRunner.run(definition, runnable);
    }

    public <T> T call(String operationName, Callable<T> callable)
            throws Exception {
        return operationRunner.call(operationName, callable);
    }

    public <T> T call(
            OperationDefinition definition,
            Callable<T> callable
    ) throws Exception {
        return operationRunner.call(definition, callable);
    }

    public TrackingExecutorService executor() {
        return executor;
    }

    public List<OperationView> operations() {
        return registry.findOperations()
                .stream()
                .map(OperationView::from)
                .toList();
    }

    public List<TaskView> tasks(UUID operationId) {
        return registry.findTasksByOperation(operationId)
                .stream()
                .map(TaskView::from)
                .toList();
    }

    public List<TaskView> ghostTasks(UUID operationId) {
        return detector.detectGhostTasks(operationId)
                .stream()
                .map(TaskView::from)
                .toList();
    }

    public List<TaskView> stuckTasks(UUID operationId, Duration threshold) {
        return detector.detectStuckTasks(operationId, threshold)
                .stream()
                .map(TaskView::from)
                .toList();
    }

    public GhostWorkReport report(Duration stuckThreshold) {
        Objects.requireNonNull(
                stuckThreshold,
                "Stuck threshold must not be null"
        );

        if (stuckThreshold.isZero() || stuckThreshold.isNegative()) {
            throw new IllegalArgumentException(
                    "Stuck threshold must be positive"
            );
        }

        List<Operation> storedOperations =
                registry.findOperations();

        List<OperationView> operations = storedOperations
                .stream()
                .map(OperationView::from)
                .toList();

        List<TaskView> tasks = storedOperations
                .stream()
                .flatMap(operation -> registry.findTasksByOperation(
                        operation.getId()
                ).stream())
                .map(TaskView::from)
                .toList();

        List<TaskView> ghostTasks = storedOperations
                .stream()
                .flatMap(operation -> detector.detectGhostTasks(
                        operation.getId()
                ).stream())
                .map(TaskView::from)
                .toList();

        List<TaskView> stuckTasks = storedOperations
                .stream()
                .flatMap(operation -> detector.detectStuckTasks(
                        operation.getId(),
                        stuckThreshold
                ).stream())
                .map(TaskView::from)
                .toList();

        return new GhostWorkReport(
                operations,
                tasks,
                ghostTasks,
                stuckTasks
        );
    }

    public GhostWorkMonitor monitor(ScheduledExecutorService scheduler) {
        return new GhostWorkMonitor(this, scheduler);
    }

    public void addEventListener(GhostWorkEventListener listener) {
        eventPublisher.addListener(listener);
    }

    public void removeEventListener(GhostWorkEventListener listener) {
        eventPublisher.removeListener(listener);
    }
}
