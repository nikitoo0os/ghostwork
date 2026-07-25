package io.nikitoo0os.entity;

import io.nikitoo0os.CancellationCause;
import io.nikitoo0os.CancellationOptions;
import io.nikitoo0os.CancellationResult;
import io.nikitoo0os.CancellationStatus;
import io.nikitoo0os.CancellationToken;
import io.nikitoo0os.ChildCancellationPolicy;
import io.nikitoo0os.CancellationDecision;
import io.nikitoo0os.CancellationPolicy;
import io.nikitoo0os.CancellationTrigger;
import io.nikitoo0os.OperationCancellationView;
import io.nikitoo0os.OperationView;
import io.nikitoo0os.TaskCancellationMode;
import io.nikitoo0os.TaskCancellationView;
import io.nikitoo0os.TaskView;
import io.nikitoo0os.entity.enums.OperationState;
import io.nikitoo0os.entity.enums.TaskState;
import io.nikitoo0os.event.GhostWorkEvent;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.event.GhostWorkEventType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public final class CancellationCoordinator {
    private final ConcurrentHashMap<UUID, Control> controls =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AtomicReference<OperationCancellationView>>
            operationViews = new ConcurrentHashMap<>();
    private final Clock clock;
    private final GhostWorkEventPublisher events;
    private volatile CancellationPolicy policy =
            CancellationPolicy.conservativeDefaults();

    public CancellationCoordinator(
            Clock clock,
            GhostWorkEventPublisher events
    ) {
        this.clock = java.util.Objects.requireNonNull(clock);
        this.events = java.util.Objects.requireNonNull(events);
    }

    public void configurePolicy(CancellationPolicy policy) {
        this.policy = java.util.Objects.requireNonNull(policy);
    }

    public void register(
            Task task,
            TaskCancellationMode mode,
            UUID parentTaskId
    ) {
        Control control = new Control(task, mode, parentTaskId);
        if (controls.putIfAbsent(task.getId(), control) != null) {
            throw new IllegalStateException(
                    "Task control already registered: " + task.getId()
            );
        }
        if (mode == TaskCancellationMode.INHERIT && parentTaskId != null) {
            Control parent = controls.get(parentTaskId);
            if (parent != null && parent.view().cancellationRequested()) {
                request(
                        control,
                        CancellationCause.PARENT_TASK_CANCELLED,
                        ChildCancellationPolicy.REQUEST_CANCELLATION
                );
            }
        }
    }

    public CancellationToken token(UUID taskId) {
        Control control = controls.get(taskId);
        return control == null
                ? CancellationToken.none()
                : control.source.token();
    }

    public TaskCancellationView view(UUID taskId) {
        Control control = controls.get(taskId);
        return control == null
                ? TaskCancellationView.none(TaskCancellationMode.INHERIT)
                : control.view();
    }

    public OperationCancellationView operationView(UUID operationId) {
        AtomicReference<OperationCancellationView> reference =
                operationViews.get(operationId);
        if (reference == null) {
            return OperationCancellationView.none();
        }
        OperationCancellationView stored = reference.get();
        List<Control> targeted = controls.values().stream()
                .filter(control -> control.task.getParentOperation().getId()
                        .equals(operationId))
                .filter(control -> control.mode == TaskCancellationMode.INHERIT)
                .filter(control -> control.view().cancellationRequested())
                .toList();
        return new OperationCancellationView(
                stored.requested(),
                stored.requestedAt(),
                stored.cause(),
                stored.targetedTasks(),
                (int) targeted.stream().filter(control ->
                        control.task.getState() == TaskState.CANCELLED).count(),
                (int) targeted.stream().filter(control ->
                        !control.task.isFinished()).count()
        );
    }

    public UUID parentTaskId(UUID taskId) {
        Control control = controls.get(taskId);
        return control == null ? null : control.parentTaskId;
    }

    public TaskCancellationMode mode(UUID taskId) {
        Control control = controls.get(taskId);
        return control == null
                ? TaskCancellationMode.INHERIT
                : control.mode;
    }

    public void attachFuture(UUID taskId, Future<?> future) {
        require(taskId).attachFuture(future);
    }

    public void markFutureUnavailable(UUID taskId) {
        require(taskId).futureUnavailable();
    }

    public boolean cancelFuture(UUID taskId, boolean interrupt) {
        Control control = require(taskId);
        if (control.task.isFinished()) {
            control.recordAttempt(false, interrupt, null);
            return false;
        }
        boolean requestAccepted = request(
                control,
                CancellationCause.FUTURE_CANCELLED,
                interrupt
                        ? ChildCancellationPolicy.INTERRUPT_RUNNING
                        : control.task.getState() == TaskState.SUBMITTED
                        ? ChildCancellationPolicy.CANCEL_QUEUED
                        : ChildCancellationPolicy.REQUEST_CANCELLATION
        );
        Future<?> future = control.future();
        if (future == null) {
            control.futureUnavailable();
            control.recordAttempt(false, interrupt, null);
            return false;
        }
        boolean accepted = cancelFuture(control, future, interrupt);
        finishQueued(control, accepted);
        if (requestAccepted) {
            propagateFrom(control);
        }
        return accepted;
    }

    public CancellationResult cancelTask(
            UUID taskId,
            CancellationOptions options
    ) {
        Instant requestedAt = Instant.now(clock);
        Control control = controls.get(taskId);
        if (control == null) {
            return CancellationResult.notFound(
                    taskId, options.cause(), requestedAt
            );
        }
        if (control.task.isFinished()) {
            return result(control, false, 0, 0, 0, 0, options.cause());
        }

        TaskState state = control.task.getState();
        boolean requestAccepted = request(
                control,
                options.cause(),
                policy(options)
        );
        int queued = state == TaskState.SUBMITTED && options.cancelQueued()
                ? 1 : 0;
        int running = state == TaskState.RUNNING
                && options.interruptRunning() ? 1 : 0;
        int attempts = 0;
        int accepted = 0;

        if (state == TaskState.CREATED) {
            cancelTaskState(control);
        } else if (queued == 1 || running == 1) {
            Future<?> future = control.future();
            if (future == null) {
                control.futureUnavailable();
            } else {
                attempts = 1;
                boolean futureAccepted = cancelFuture(
                        control, future, running == 1
                );
                accepted = futureAccepted ? 1 : 0;
                finishQueued(control, futureAccepted);
            }
        }
        if (requestAccepted) {
            propagateFrom(control);
        }

        return result(
                control, requestAccepted, queued, running,
                attempts, accepted, options.cause()
        );
    }

    public CancellationResult cancelOperation(
            Operation operation,
            CancellationOptions options,
            boolean finishAsCancelled
    ) {
        Instant requestedAt = Instant.now(clock);
        OperationState stateBefore = operation.getState();
        boolean operationCancelled = finishAsCancelled
                && operation.tryFinish(OperationState.CANCELLED);
        if (operationCancelled) {
            publish(
                    GhostWorkEventType.OPERATION_CANCELLED,
                    operation,
                    null,
                    null,
                    options.cause()
            );
        }
        List<Control> active = controls.values().stream()
                .filter(control -> control.task.getParentOperation().getId()
                        .equals(operation.getId()))
                .filter(control -> !control.task.isFinished())
                .filter(control -> control.mode == TaskCancellationMode.INHERIT)
                .toList();

        int queued = 0;
        int running = 0;
        int attempts = 0;
        int accepted = 0;
        boolean taskRequestAccepted = false;
        for (Control control : active) {
            TaskState state = control.task.getState();
            taskRequestAccepted |= request(
                    control,
                    options.cause(),
                    policy(options)
            );
            if (state == TaskState.SUBMITTED && options.cancelQueued()) {
                queued++;
                Future<?> future = control.future();
                if (future == null) {
                    control.futureUnavailable();
                } else {
                    attempts++;
                    boolean cancelled = cancelFuture(control, future, false);
                    accepted += cancelled ? 1 : 0;
                    finishQueued(control, cancelled);
                }
            } else if (state == TaskState.RUNNING
                    && options.interruptRunning()) {
                running++;
                Future<?> future = control.future();
                if (future == null) {
                    control.futureUnavailable();
                } else {
                    attempts++;
                    accepted += cancelFuture(control, future, true) ? 1 : 0;
                }
            }
        }

        int cancelled = (int) active.stream()
                .filter(control -> control.task.getState() == TaskState.CANCELLED)
                .count();
        int stillActive = (int) active.stream()
                .filter(control -> !control.task.isFinished())
                .count();
        OperationCancellationView cancellation = new OperationCancellationView(
                true, requestedAt, options.cause(),
                active.size(), cancelled, stillActive
        );
        AtomicReference<OperationCancellationView> reference =
                operationViews.computeIfAbsent(
                        operation.getId(),
                        ignored -> new AtomicReference<>(
                                OperationCancellationView.none()
                        )
                );
        OperationCancellationView current = reference.get();
        boolean operationRequestAccepted = !current.requested()
                && reference.compareAndSet(current, cancellation);
        if (operationRequestAccepted) {
            publish(
                    GhostWorkEventType.OPERATION_CANCELLATION_REQUESTED,
                    operation, null, null, options.cause()
            );
        }
        return new CancellationResult(
                operation.getId(),
                true,
                operationRequestAccepted || taskRequestAccepted,
                active.size(),
                queued,
                running,
                attempts,
                accepted,
                stillActive,
                requestedAt,
                options.cause(),
                stateBefore
        );
    }

    public void markInterruptObserved(UUID taskId) {
        Control control = require(taskId);
        control.interruptObserved();
        publish(
                GhostWorkEventType.TASK_CANCELLATION_OBSERVED,
                control.task.getParentOperation(),
                control.task,
                null,
                control.view().cancellationCause()
        );
    }

    public void cancellationCompleted(UUID taskId) {
        Control control = require(taskId);
        control.cancelled(Instant.now(clock));
        publish(
                GhostWorkEventType.TASK_CANCELLATION_COMPLETED,
                control.task.getParentOperation(),
                control.task,
                null,
                control.view().cancellationCause()
        );
        terminal(taskId);
    }

    public void terminal(UUID taskId) {
        Control control = controls.get(taskId);
        if (control != null) {
            control.close();
        }
    }

    public int markGracePeriodExceeded(Duration gracePeriod) {
        java.util.Objects.requireNonNull(gracePeriod);
        Instant now = Instant.now(clock);
        int changed = 0;
        for (Control control : controls.values()) {
            TaskCancellationView view = control.view();
            if (!control.task.isFinished()
                    && view.cancellationRequested()
                    && view.cancellationRequestedAt() != null
                    && !view.gracePeriodExceeded()
                    && !now.isBefore(view.cancellationRequestedAt()
                    .plus(gracePeriod))
                    && control.graceExceeded()) {
                changed++;
                publish(
                        GhostWorkEventType
                                .TASK_CANCELLATION_GRACE_PERIOD_EXCEEDED,
                        control.task.getParentOperation(),
                        control.task,
                        null,
                        view.cancellationCause()
                );
            }
        }
        return changed;
    }

    public void propagateParent(
            UUID parentTaskId,
            CancellationOptions options
    ) {
        propagateParent(parentTaskId, options, new HashSet<>());
    }

    private void propagateFrom(Control parent) {
        List<Control> children = controls.values().stream()
                .filter(control -> parent.task.getId()
                        .equals(control.parentTaskId))
                .filter(control -> control.mode == TaskCancellationMode.INHERIT)
                .filter(control -> !control.task.isFinished())
                .toList();
        if (children.isEmpty()) {
            return;
        }
        CancellationDecision decision = policy.decide(
                OperationView.from(parent.task.getParentOperation()),
                CancellationTrigger.PARENT_TASK_CANCEL,
                children.stream().map(control ->
                        TaskView.from(control.task)).toList()
        );
        if (decision.policy() == ChildCancellationPolicy.NONE) {
            return;
        }
        CancellationOptions options = new CancellationOptions(
                CancellationCause.PARENT_TASK_CANCELLED,
                decision.policy().cancelQueued(),
                decision.policy().interruptRunning()
        );
        children.forEach(child -> cancelTask(child.task.getId(), options));
    }

    private void propagateParent(
            UUID parentTaskId,
            CancellationOptions options,
            Set<UUID> visited
    ) {
        if (!visited.add(parentTaskId)) {
            return;
        }
        List<Control> children = controls.values().stream()
                .filter(control -> parentTaskId.equals(control.parentTaskId))
                .filter(control -> control.mode == TaskCancellationMode.INHERIT)
                .toList();
        for (Control child : children) {
            cancelTask(child.task.getId(), options);
            propagateParent(child.task.getId(), options, visited);
        }
    }

    public List<UUID> taskIds() {
        return List.copyOf(controls.keySet());
    }

    private boolean request(
            Control control,
            CancellationCause cause,
            ChildCancellationPolicy policy
    ) {
        Instant now = Instant.now(clock);
        if (!control.request(cause, now, policy)) {
            return false;
        }
        control.source.request(cause, now);
        publish(
                GhostWorkEventType.TASK_CANCELLATION_REQUESTED,
                control.task.getParentOperation(),
                control.task,
                null,
                cause
        );
        return true;
    }

    private static ChildCancellationPolicy policy(
            CancellationOptions options
    ) {
        if (options.cancelQueued() && options.interruptRunning()) {
            return ChildCancellationPolicy.CANCEL_ALL;
        }
        if (options.cancelQueued()) {
            return ChildCancellationPolicy.CANCEL_QUEUED;
        }
        if (options.interruptRunning()) {
            return ChildCancellationPolicy.INTERRUPT_RUNNING;
        }
        return ChildCancellationPolicy.REQUEST_CANCELLATION;
    }

    private boolean cancelFuture(
            Control control,
            Future<?> future,
            boolean interrupt
    ) {
        boolean accepted = false;
        Throwable failure = null;
        try {
            accepted = future.cancel(interrupt);
            return accepted;
        } catch (Throwable caught) {
            failure = caught;
            return false;
        } finally {
            control.recordAttempt(accepted, interrupt, failure);
            if (accepted) {
                publish(
                        GhostWorkEventType.TASK_CANCELLATION_ACCEPTED,
                        control.task.getParentOperation(),
                        control.task,
                        failure,
                        control.view().cancellationCause()
                );
            }
            if (interrupt) {
                publish(
                        GhostWorkEventType.TASK_INTERRUPT_REQUESTED,
                        control.task.getParentOperation(),
                        control.task,
                        null,
                        control.view().cancellationCause()
                );
            }
        }
    }

    private void finishQueued(Control control, boolean accepted) {
        if (accepted && control.task.getState() == TaskState.SUBMITTED) {
            cancelTaskState(control);
        }
    }

    private void cancelTaskState(Control control) {
        try {
            control.task.cancel(Instant.now(clock));
            cancellationCompleted(control.task.getId());
            publish(
                    GhostWorkEventType.TASK_CANCELLED,
                    control.task.getParentOperation(),
                    control.task,
                    null,
                    control.view().cancellationCause()
            );
        } catch (IllegalStateException ignored) {
            // Start or a terminal transition won the race.
        }
    }

    private CancellationResult result(
            Control control,
            boolean requestAccepted,
            int queued,
            int running,
            int attempts,
            int accepted,
            CancellationCause cause
    ) {
        return new CancellationResult(
                control.task.getId(),
                true,
                requestAccepted,
                control.task.isFinished() ? 0 : 1,
                queued,
                running,
                attempts,
                accepted,
                control.task.isFinished() ? 0 : 1,
                control.view().cancellationRequestedAt(),
                cause
        );
    }

    private Control require(UUID taskId) {
        Control control = controls.get(taskId);
        if (control == null) {
            throw new java.util.NoSuchElementException(
                    "Task with id: " + taskId + " not found"
            );
        }
        return control;
    }

    private void publish(
            GhostWorkEventType type,
            Operation operation,
            Task task,
            Throwable failure,
            CancellationCause cause
    ) {
        events.publish(new GhostWorkEvent(
                type,
                OperationView.from(operation),
                task == null ? null : TaskView.from(task),
                failure,
                cause,
                Instant.now(clock)
        ));
    }

    private final class Control {
        private final Task task;
        private final TaskCancellationMode mode;
        private final UUID parentTaskId;
        private final AtomicReference<Future<?>> future = new AtomicReference<>();
        private final CancellationTokenSource source;
        private TaskCancellationView view;

        private Control(
                Task task,
                TaskCancellationMode mode,
                UUID parentTaskId
        ) {
            this.task = task;
            this.mode = mode;
            this.parentTaskId = parentTaskId;
            this.view = TaskCancellationView.none(mode);
            this.source = new CancellationTokenSource(
                    this::cooperativeObserved,
                    this::callbackExecuted,
                    this::callbackFailed
            );
        }

        private synchronized TaskCancellationView view() {
            return view;
        }

        private synchronized boolean request(
                CancellationCause cause,
                Instant requestedAt,
                ChildCancellationPolicy policy
        ) {
            if (task.isFinished() || view.cancellationRequested()) {
                return false;
            }
            view = replace(
                    CancellationStatus.PENDING, true, requestedAt, cause,
                    null, null, null, null, null, null, null, null, null
            );
            view = withPolicy(view, policy);
            return true;
        }

        private synchronized void attachFuture(Future<?> attached) {
            future.set(java.util.Objects.requireNonNull(attached));
            view = replace(
                    null, null, null, null, true, null, null,
                    null, null, null, null, null, null
            );
        }

        private Future<?> future() {
            return future.get();
        }

        private synchronized void futureUnavailable() {
            view = replace(
                    view.cancellationRequested()
                            ? CancellationStatus.UNAVAILABLE : null,
                    null, null, null, false, null, null,
                    null, null, null, null, null, null
            );
        }

        private synchronized void recordAttempt(
                boolean accepted,
                boolean interrupt,
                Throwable failure
        ) {
            view = replace(
                    null, null, null, null, future.get() != null,
                    true, accepted, interrupt, null, null, null, null,
                    failure == null ? null : failure.toString()
            );
        }

        private synchronized void interruptObserved() {
            view = replace(
                    null, null, null, null, null, null, null,
                    null, true, null, null, null, null
            );
        }

        private void cooperativeObserved() {
            CancellationCause cause;
            synchronized (this) {
                if (view.cooperativeCancellationObserved()) {
                    return;
                }
                view = replace(
                        null, null, null, null, null, null, null,
                        null, null, true, null, null, null
                );
                cause = view.cancellationCause();
            }
            publish(
                    GhostWorkEventType.TASK_CANCELLATION_OBSERVED,
                    task.getParentOperation(), task, null, cause
            );
        }

        private synchronized void callbackExecuted() {
            view = new TaskCancellationView(
                    view.status(),
                    view.cancellationRequested(),
                    view.cancellationRequestedAt(),
                    view.cancellationCause(),
                    view.futureCancellationAvailable(),
                    view.futureCancellationAttempted(),
                    view.futureCancellationAccepted(),
                    view.interruptRequested(),
                    view.interruptObserved(),
                    view.cooperativeCancellationObserved(),
                    view.cancelledAt(),
                    view.gracePeriodExceeded(),
                    view.cancellationCallbackCount() + 1,
                    view.cancellationError(),
                    view.mode(),
                    view.policy()
            );
        }

        private void callbackFailed(Throwable failure) {
            CancellationCause cause;
            synchronized (this) {
                view = replace(
                        null, null, null, null, null, null, null,
                        null, null, null, null, null, failure.toString()
                );
                cause = view.cancellationCause();
            }
            publish(
                    GhostWorkEventType.CANCELLATION_CALLBACK_FAILED,
                    task.getParentOperation(), task, failure, cause
            );
        }

        private synchronized void cancelled(Instant cancelledAt) {
            view = replace(
                    CancellationStatus.CANCELLED, null, null, null,
                    null, null, null, null, null, null,
                    cancelledAt, null, null
            );
        }

        private synchronized boolean graceExceeded() {
            if (view.gracePeriodExceeded()) {
                return false;
            }
            view = replace(
                    CancellationStatus.IGNORED, null, null, null,
                    null, null, null, null, null, null,
                    null, true, null
            );
            return true;
        }

        private void close() {
            source.close();
            future.set(null);
        }

        private TaskCancellationView replace(
                CancellationStatus status,
                Boolean requested,
                Instant requestedAt,
                CancellationCause cause,
                Boolean futureAvailable,
                Boolean futureAttempted,
                Boolean futureAccepted,
                Boolean interruptRequested,
                Boolean interruptObserved,
                Boolean cooperativeObserved,
                Instant cancelledAt,
                Boolean graceExceeded,
                String error
        ) {
            return new TaskCancellationView(
                    status == null ? view.status() : status,
                    requested == null ? view.cancellationRequested() : requested,
                    requestedAt == null
                            ? view.cancellationRequestedAt() : requestedAt,
                    cause == null ? view.cancellationCause() : cause,
                    futureAvailable == null
                            ? view.futureCancellationAvailable()
                            : futureAvailable,
                    futureAttempted == null
                            ? view.futureCancellationAttempted()
                            : futureAttempted,
                    futureAccepted == null
                            ? view.futureCancellationAccepted()
                            : futureAccepted,
                    interruptRequested == null
                            ? view.interruptRequested()
                            : interruptRequested,
                    interruptObserved == null
                            ? view.interruptObserved()
                            : interruptObserved,
                    cooperativeObserved == null
                            ? view.cooperativeCancellationObserved()
                            : cooperativeObserved,
                    cancelledAt == null ? view.cancelledAt() : cancelledAt,
                    graceExceeded == null
                            ? view.gracePeriodExceeded()
                            : graceExceeded,
                    view.cancellationCallbackCount(),
                    error == null ? view.cancellationError() : error,
                    mode,
                    view.policy()
            );
        }

        private TaskCancellationView withPolicy(
                TaskCancellationView current,
                ChildCancellationPolicy policy
        ) {
            return new TaskCancellationView(
                    current.status(),
                    current.cancellationRequested(),
                    current.cancellationRequestedAt(),
                    current.cancellationCause(),
                    current.futureCancellationAvailable(),
                    current.futureCancellationAttempted(),
                    current.futureCancellationAccepted(),
                    current.interruptRequested(),
                    current.interruptObserved(),
                    current.cooperativeCancellationObserved(),
                    current.cancelledAt(),
                    current.gracePeriodExceeded(),
                    current.cancellationCallbackCount(),
                    current.cancellationError(),
                    current.mode(),
                    policy
            );
        }
    }
}
