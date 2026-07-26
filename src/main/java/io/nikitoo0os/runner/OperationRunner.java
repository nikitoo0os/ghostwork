package io.nikitoo0os.runner;

import io.nikitoo0os.OperationView;
import io.nikitoo0os.context.OperationContext;
import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.event.GhostWorkEvent;
import io.nikitoo0os.event.GhostWorkEventPublisher;
import io.nikitoo0os.event.GhostWorkEventType;
import io.nikitoo0os.operation.OperationDefinition;
import io.nikitoo0os.entity.enums.OperationState;

import java.util.Objects;
import java.util.concurrent.Callable;

public final class OperationRunner {
    private final Registry registry;

    private final GhostWorkEventPublisher eventPublisher;

    public OperationRunner(
            Registry registry,
            GhostWorkEventPublisher eventPublisher
    ) {
        this.registry = Objects.requireNonNull(
                registry,
                "Registry must not be null"
        );
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher,
                "Event publisher must not be null"
        );
    }

    public void run(String operationName, Runnable runnable) {
        Objects.requireNonNull(runnable, "Runnable must not be null");
        Operation operation = new Operation(operationName);
        registry.registerOperation(operation);
        publishStarted(operation);
        try (OperationContext.Scope ignored = OperationContext.open(operation)) {
            runnable.run();
            if (operation.tryFinish(OperationState.COMPLETED)) {
                eventPublisher.publish(
                    new GhostWorkEvent(
                            GhostWorkEventType.OPERATION_COMPLETED,
                            OperationView.from(operation),
                            null,
                            null
                    )
                );
            }
        } catch (Throwable original) {
            if (operation.tryFinish(OperationState.FAILED)) {
                eventPublisher.publish(
                        new GhostWorkEvent(
                                GhostWorkEventType.OPERATION_FAILED,
                                OperationView.from(operation),
                                null,
                                original
                        )
                );
            }
            throw original;
        }
    }

    public void run(OperationDefinition definition, Runnable runnable) {
        Objects.requireNonNull(definition, "Operation definition must not be null");
        run(definition.name(), runnable);
    }

    public <T> T call(String operationName, Callable<T> callable) throws Exception {
        Objects.requireNonNull(callable, "Callable must not be null");
        Operation operation = new Operation(operationName);
        registry.registerOperation(operation);
        publishStarted(operation);

        try (OperationContext.Scope ignored = OperationContext.open(operation)) {
            T result = callable.call();
            if (operation.tryFinish(OperationState.COMPLETED)) {
                eventPublisher.publish(
                    new GhostWorkEvent(
                            GhostWorkEventType.OPERATION_COMPLETED,
                            OperationView.from(operation),
                            null,
                            null
                    )
                );
            }
            return result;
        } catch (Throwable original) {
            if (operation.tryFinish(OperationState.FAILED)) {
                eventPublisher.publish(
                        new GhostWorkEvent(
                                GhostWorkEventType.OPERATION_FAILED,
                                OperationView.from(operation),
                                null,
                                original
                        )
                );
            }
            throw original;
        }
    }

    public <T> T call(
            OperationDefinition definition,
            Callable<T> callable
    ) throws Exception {
        Objects.requireNonNull(definition, "Operation definition must not be null");
        return call(definition.name(), callable);
    }

    private void publishStarted(Operation operation) {
        eventPublisher.publishOperationStarted(OperationView.from(operation));
    }

}
