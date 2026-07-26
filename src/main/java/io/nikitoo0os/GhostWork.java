package io.nikitoo0os;

import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.CancellationCoordinator;
import io.nikitoo0os.event.GhostWorkEventListener;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.event.GhostWorkLifecycleEventListener;
import io.nikitoo0os.event.GhostWorkEvent;
import io.nikitoo0os.event.GhostWorkEventType;
import io.nikitoo0os.factory.TrackingCallableFactory;
import io.nikitoo0os.factory.TrackingRunnableFactory;
import io.nikitoo0os.operation.OperationDefinition;
import io.nikitoo0os.runner.OperationRunner;
import io.nikitoo0os.scheduling.ScheduleExecutionQuery;
import io.nikitoo0os.scheduling.ScheduleExecutionView;
import io.nikitoo0os.scheduling.ScheduleId;
import io.nikitoo0os.scheduling.ScheduleQuery;
import io.nikitoo0os.scheduling.ScheduleRegistry;
import io.nikitoo0os.scheduling.ScheduleRetentionPolicy;
import io.nikitoo0os.scheduling.ScheduleView;
import io.nikitoo0os.scheduling.TrackingScheduledExecutorService;
import io.nikitoo0os.scheduling.ExternalScheduleTracker;
import io.nikitoo0os.scheduling.ScheduleOptions;
import io.nikitoo0os.scheduling.ScheduleTrigger;
import io.nikitoo0os.scheduling.ScheduleType;
import io.nikitoo0os.scheduling.ScheduleEventListener;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class GhostWork {
    private final Registry registry;
    private final OperationRunner operationRunner;
    private final TrackingExecutorService executor;
    private final GhostWorkEventPublisher eventPublisher;
    private final Detector detector;
    private final Clock clock;
    private final RetentionPolicy retentionPolicy;
    private final CancellationCoordinator cancellation;
    private final ScheduleRegistry scheduleRegistry;
    private volatile CancellationPolicy cancellationPolicy;

    private GhostWork(
            Registry registry,
            OperationRunner operationRunner,
            TrackingExecutorService executor,
            Detector detector,
            GhostWorkEventPublisher eventPublisher,
            Clock clock,
            RetentionPolicy retentionPolicy,
            CancellationCoordinator cancellation,
            CancellationPolicy cancellationPolicy
    ) {
        this.registry = Objects.requireNonNull(registry, "Registry must not be null");
        this.operationRunner = Objects.requireNonNull(operationRunner, "Operation runner must not be null");
        this.executor = Objects.requireNonNull(executor, "Executor must not be null");
        this.detector = Objects.requireNonNull(detector, "Detector must not be null");
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher,
                "Event publisher must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.retentionPolicy = Objects.requireNonNull(
                retentionPolicy,
                "Retention policy must not be null"
        );
        this.cancellation = Objects.requireNonNull(cancellation);
        this.scheduleRegistry = new ScheduleRegistry(
                registry::retainOperation,
                registry::releaseOperation,
                clock,
                eventPublisher,
                operationId -> registry.findOperation(operationId)
                        .getCorrelationId()
        );
        this.cancellationPolicy = Objects.requireNonNull(cancellationPolicy);
        this.cancellation.configurePolicy(cancellationPolicy);
    }

    public static GhostWork create(ExecutorService delegate) {
        return create(delegate, RetentionPolicy.defaults());
    }

    public static GhostWork create(
            ExecutorService delegate,
            RetentionPolicy retentionPolicy
    ) {
        return create(
                delegate,
                retentionPolicy,
                CancellationPolicy.conservativeDefaults()
        );
    }

    public static GhostWork create(
            ExecutorService delegate,
            RetentionPolicy retentionPolicy,
            CancellationPolicy cancellationPolicy
    ) {
        Objects.requireNonNull(delegate, "Delegate executor must not be null");
        Objects.requireNonNull(
                retentionPolicy,
                "Retention policy must not be null"
        );
        Registry registry = new Registry();
        Clock clock = Clock.systemUTC();
        Detector detector = new Detector(registry, clock);

        GhostWorkEventPublisher eventPublisher =
                new GhostWorkEventPublisher(clock);

        TrackingRunnableFactory runnableFactory =
                new TrackingRunnableFactory(registry, clock, eventPublisher);

        TrackingCallableFactory callableFactory =
                new TrackingCallableFactory(registry, clock, eventPublisher);
        CancellationCoordinator cancellation =
                registry.cancellationCoordinator(clock, eventPublisher);

        TrackingExecutorService executor =
                new TrackingExecutorService(
                        delegate,
                        runnableFactory,
                        callableFactory,
                        clock,
                        eventPublisher,
                        registry
                );


        OperationRunner operationRunner =
                new OperationRunner(registry, eventPublisher);

        return new GhostWork(
                registry,
                operationRunner,
                executor,
                detector,
                eventPublisher,
                clock,
                retentionPolicy,
                cancellation,
                cancellationPolicy
        );
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

    public void configureCancellationPolicy(CancellationPolicy policy) {
        this.cancellationPolicy = Objects.requireNonNull(
                policy,
                "Cancellation policy must not be null"
        );
        cancellation.configurePolicy(policy);
    }

    public OperationHandle startOperation(
            String name,
            OperationMetadata metadata
    ) {
        return startOperation(
                name,
                metadata,
                GhostWorkContext.currentCorrelationId()
                        .orElseGet(CorrelationId::random)
        );
    }

    public OperationHandle startOperation(
            String name,
            OperationMetadata metadata,
            CorrelationId correlationId
    ) {
        Operation operation = new Operation(
                name,
                metadata,
                correlationId
        );
        registry.registerOperation(operation);
        eventPublisher.publishOperationStarted(OperationView.from(operation));
        return new OperationHandle(
                operation,
                eventPublisher,
                cancellation,
                cancellationPolicy,
                registry
        );
    }

    public OperationDetails operationDetails(UUID operationId) {
        Operation operation = registry.findOperation(operationId);
        java.time.Instant observedAt = java.time.Instant.now(clock);
        List<TaskDiagnostics> tasks = registry
                .findTasksByOperation(operationId)
                .stream()
                .map(task -> diagnostics(task, observedAt))
                .toList();
        List<TaskDiagnostics> ghosts = detector
                .detectOutlivedTasks(operationId)
                .stream()
                .filter(task -> cancellation.mode(task.getId())
                        != TaskCancellationMode.DETACHED)
                .map(task -> diagnostics(task, observedAt))
                .toList();
        List<TimelineEntry> timeline = new java.util.ArrayList<>();
        timeline.add(new TimelineEntry(
                operation.getStartedAt(),
                "OPERATION_STARTED",
                "Operation started",
                null
        ));
        tasks.forEach(task -> {
            if (task.submittedAt() != null) {
                timeline.add(new TimelineEntry(
                        task.submittedAt(),
                        "TASK_SUBMITTED",
                        task.taskName() + " submitted",
                        task.taskId()
                ));
            }
            if (task.startedAt() != null) {
                timeline.add(new TimelineEntry(
                        task.startedAt(),
                        "TASK_STARTED",
                        task.taskName() + " started",
                        task.taskId()
                ));
            }
            if (task.finishedAt() != null) {
                timeline.add(new TimelineEntry(
                        task.finishedAt(),
                        "TASK_FINISHED",
                        task.taskName() + " " + task.state(),
                        task.taskId()
                ));
            }
        });
        if (operation.getFinishedAt() != null) {
            timeline.add(new TimelineEntry(
                    operation.getFinishedAt(),
                    "OPERATION_FINISHED",
                    "Operation " + operation.getState(),
                    null
            ));
        }
        timeline.sort(java.util.Comparator.comparing(TimelineEntry::timestamp));
        return new OperationDetails(
                OperationView.from(operation),
                operation.getMetadata(),
                tasks,
                ghosts,
                timeline,
                cancellation.operationView(operationId)
        );
    }

    public TrackingExecutorService decorate(
            ExecutorService delegate,
            ExecutorMetadata metadata
    ) {
        Objects.requireNonNull(delegate, "Delegate executor must not be null");
        Objects.requireNonNull(metadata, "Executor metadata must not be null");
        return executor.decorate(delegate, metadata);
    }

    public TrackingScheduledExecutorService decorateScheduler(
            ScheduledExecutorService delegate
    ) {
        return decorateScheduler(
                delegate,
                ExecutorMetadata.manual(delegate.getClass())
        );
    }

    public ExternalScheduleTracker trackSchedule(
            ScheduleOptions options,
            ScheduleType type,
            ScheduleTrigger trigger
    ) {
        return ExternalScheduleTracker.create(
                this,
                scheduleRegistry,
                clock,
                Objects.requireNonNull(options),
                Objects.requireNonNull(type),
                Objects.requireNonNull(trigger)
        );
    }

    public TrackingScheduledExecutorService decorateScheduler(
            ScheduledExecutorService delegate,
            ExecutorMetadata metadata
    ) {
        return new TrackingScheduledExecutorService(
                this,
                Objects.requireNonNull(delegate),
                scheduleRegistry,
                clock,
                Objects.requireNonNull(metadata)
        );
    }

    public ScheduleView schedule(ScheduleId scheduleId) {
        return scheduleRegistry.find(scheduleId);
    }

    public List<ScheduleView> schedules() {
        return schedules(ScheduleQuery.firstPage());
    }

    public List<ScheduleView> schedules(ScheduleQuery query) {
        return scheduleRegistry.find(query);
    }

    public long scheduleCount(java.util.Set<io.nikitoo0os.scheduling.ScheduleState> states) {
        return scheduleRegistry.count(states);
    }

    public List<ScheduleExecutionView> scheduleExecutions(
            ScheduleId scheduleId
    ) {
        return scheduleExecutions(
                scheduleId,
                ScheduleExecutionQuery.recent()
        );
    }

    public List<ScheduleExecutionView> scheduleExecutions(
            ScheduleId scheduleId,
            ScheduleExecutionQuery query
    ) {
        return scheduleRegistry.executions(scheduleId, query);
    }

    public long scheduleExecutionCount(
            ScheduleId scheduleId,
            java.util.Set<io.nikitoo0os.scheduling.ScheduleExecutionState> states
    ) {
        return scheduleRegistry.countExecutions(scheduleId, states);
    }

    public int cleanupSchedules(ScheduleRetentionPolicy policy) {
        return scheduleRegistry.cleanup(
                Objects.requireNonNull(policy),
                clock.instant()
        );
    }

    public void addScheduleListener(ScheduleEventListener listener) {
        scheduleRegistry.addListener(listener);
    }

    public void removeScheduleListener(ScheduleEventListener listener) {
        scheduleRegistry.removeListener(listener);
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
                .filter(task -> cancellation.mode(task.getId())
                        != TaskCancellationMode.DETACHED)
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
                .filter(task -> cancellation.mode(task.getId())
                        != TaskCancellationMode.DETACHED)
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

    public List<TaskView> outlivedTasks(UUID operationId) {
        return detector.detectOutlivedTasks(operationId)
                .stream()
                .filter(task -> cancellation.mode(task.getId())
                        != TaskCancellationMode.DETACHED)
                .map(TaskView::from)
                .toList();
    }

    public List<TaskDiagnostics> taskDiagnostics(UUID operationId) {
        java.time.Instant observedAt = java.time.Instant.now(clock);
        return registry.findTasksByOperation(operationId)
                .stream()
                .map(task -> diagnostics(task, observedAt))
                .toList();
    }

    public List<TaskDiagnostics> stuckQueuedTasks(
            UUID operationId,
            Duration threshold
    ) {
        java.time.Instant observedAt = java.time.Instant.now(clock);
        return detector.detectStuckQueuedTasks(operationId, threshold)
                .stream()
                .map(task -> diagnostics(task, observedAt))
                .toList();
    }

    public List<TaskDiagnostics> stuckRunningTasks(
            UUID operationId,
            Duration threshold
    ) {
        java.time.Instant observedAt = java.time.Instant.now(clock);
        return detector.detectStuckTasks(operationId, threshold)
                .stream()
                .map(task -> diagnostics(task, observedAt))
                .toList();
    }

    public TaskCancellationView taskCancellation(UUID taskId) {
        registry.findTask(taskId);
        return cancellation.view(taskId);
    }

    public CancellationResult cancelTask(UUID taskId) {
        return cancelTask(
                taskId,
                CancellationOptions.cancelAll(
                        CancellationCause.USER_REQUEST
                )
        );
    }

    public CancellationResult cancelTask(
            UUID taskId,
            CancellationOptions options
    ) {
        Objects.requireNonNull(taskId, "Task id must not be null");
        Objects.requireNonNull(options, "Cancellation options must not be null");
        return cancellation.cancelTask(taskId, options);
    }

    public CancellationResult cancelOperation(UUID operationId) {
        return cancelOperation(
                operationId,
                CancellationCause.USER_REQUEST
        );
    }

    public CancellationResult cancelOperation(
            UUID operationId,
            CancellationCause cause
    ) {
        Objects.requireNonNull(operationId, "Operation id must not be null");
        Objects.requireNonNull(cause, "Cancellation cause must not be null");
        Operation operation;
        try {
            operation = registry.findOperation(operationId);
        } catch (java.util.NoSuchElementException missing) {
            return CancellationResult.notFound(
                    operationId,
                    cause,
                    java.time.Instant.now(clock)
            );
        }
        CancellationDecision decision = cancellationPolicy.decide(
                OperationView.from(operation),
                CancellationTrigger.OPERATION_CANCEL,
                registry.findTasksByOperation(operationId)
                        .stream()
                        .filter(task -> !task.isFinished())
                        .map(TaskView::from)
                        .toList()
        );
        return cancellation.cancelOperation(
                operation,
                new CancellationOptions(
                        cause,
                        decision.policy().cancelQueued(),
                        decision.policy().interruptRunning()
                ),
                true
        );
    }

    public CancellationResult propagateCancellation(
            UUID operationId,
            CancellationTrigger trigger,
            CancellationCause cause
    ) {
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(trigger);
        Objects.requireNonNull(cause);
        Operation operation;
        try {
            operation = registry.findOperation(operationId);
        } catch (java.util.NoSuchElementException missing) {
            return CancellationResult.notFound(
                    operationId,
                    cause,
                    java.time.Instant.now(clock)
            );
        }
        List<TaskView> active = registry.findTasksByOperation(operationId)
                .stream()
                .filter(task -> !task.isFinished())
                .map(TaskView::from)
                .toList();
        CancellationDecision decision = cancellationPolicy.decide(
                OperationView.from(operation),
                trigger,
                active
        );
        if (decision.policy() == ChildCancellationPolicy.NONE) {
            return new CancellationResult(
                    operationId,
                    true,
                    false,
                    active.size(),
                    0,
                    0,
                    0,
                    0,
                    active.size(),
                    java.time.Instant.now(clock),
                    cause,
                    operation.getState()
            );
        }
        return cancellation.cancelOperation(
                operation,
                new CancellationOptions(
                        cause,
                        decision.policy().cancelQueued(),
                        decision.policy().interruptRunning()
                ),
                false
        );
    }

    public List<TaskDiagnostics> cancellationPendingTasks() {
        return cancellationTasks(CancellationStatus.PENDING);
    }

    public List<TaskDiagnostics> cancellationIgnoredTasks() {
        return cancellationTasks(CancellationStatus.IGNORED);
    }

    public List<TaskDiagnostics> cancelledTasks() {
        return cancellationTasks(CancellationStatus.CANCELLED);
    }

    public int refreshCancellationDiagnostics(Duration gracePeriod) {
        return cancellation.markGracePeriodExceeded(gracePeriod);
    }

    private List<TaskDiagnostics> cancellationTasks(
            CancellationStatus status
    ) {
        java.time.Instant observedAt = java.time.Instant.now(clock);
        return registry.findOperations().stream()
                .flatMap(operation -> registry
                        .findTasksByOperation(operation.getId()).stream())
                .filter(task -> cancellation.view(task.getId()).status()
                        == status)
                .map(task -> diagnostics(task, observedAt))
                .toList();
    }

    private TaskDiagnostics diagnostics(
            io.nikitoo0os.entity.Task task,
            java.time.Instant observedAt
    ) {
        return TaskDiagnostics.from(
                task,
                observedAt,
                cancellation.parentTaskId(task.getId()),
                cancellation.view(task.getId())
        );
    }

    public int cleanup() {
        return registry.cleanupCompletedOperations(
                retentionPolicy.maxCompletedOperations(),
                retentionPolicy.completedOperationTtl(),
                java.time.Instant.now(clock)
        );
    }

    public ScheduledFuture<?> startRetentionCleanup(
            ScheduledExecutorService scheduler
    ) {
        Objects.requireNonNull(scheduler, "Scheduler must not be null");
        long intervalMillis =
                retentionPolicy.cleanupInterval().toMillis();

        return scheduler.scheduleWithFixedDelay(
                this::cleanup,
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public void addEventListener(GhostWorkEventListener listener) {
        eventPublisher.addListener(listener);
    }

    public void removeEventListener(GhostWorkEventListener listener) {
        eventPublisher.removeListener(listener);
    }

    public void addLifecycleEventListener(
            GhostWorkLifecycleEventListener listener
    ) {
        eventPublisher.addLifecycleListener(listener);
    }

    public void removeLifecycleEventListener(
            GhostWorkLifecycleEventListener listener
    ) {
        eventPublisher.removeLifecycleListener(listener);
    }
}
