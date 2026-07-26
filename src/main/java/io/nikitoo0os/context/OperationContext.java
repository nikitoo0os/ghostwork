package io.nikitoo0os.context;

import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.GhostWorkContext;

import java.util.Objects;
import java.util.Optional;

public final class OperationContext {

    private static final ThreadLocal<Operation> CURRENT_OPERATION =
            new ThreadLocal<>();

    private OperationContext() {
    }

    public static void set(Operation operation) {
        CURRENT_OPERATION.set(
                Objects.requireNonNull(
                        operation,
                        "Operation must not be null"
                )
        );
    }

    public static Scope open(Operation operation) {
        Operation previousOperation = CURRENT_OPERATION.get();

        Operation current = Objects.requireNonNull(
                operation,
                "Operation must not be null"
        );
        CURRENT_OPERATION.set(current);

        return new Scope(
                previousOperation,
                GhostWorkContext.openOperation(
                        current.getId(),
                        current.getCorrelationId()
                )
        );
    }

    public static Optional<Operation> current() {
        return Optional.ofNullable(CURRENT_OPERATION.get());
    }

    public static void clear() {
        CURRENT_OPERATION.remove();
    }

    public static final class Scope implements AutoCloseable {

        private final Operation previousOperation;
        private final GhostWorkContext.Scope contextScope;

        private Scope(
                Operation previousOperation,
                GhostWorkContext.Scope contextScope
        ) {
            this.previousOperation = previousOperation;
            this.contextScope = contextScope;
        }

        @Override
        public void close() {
            contextScope.close();
            if (previousOperation == null) {
                CURRENT_OPERATION.remove();
            } else {
                CURRENT_OPERATION.set(previousOperation);
            }
        }
    }
}
