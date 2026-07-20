package io.nikitoo0os.context;

import io.nikitoo0os.entity.Operation;

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

        CURRENT_OPERATION.set(
                Objects.requireNonNull(
                        operation,
                        "Operation must not be null"
                )
        );

        return new Scope(previousOperation);
    }

    public static Optional<Operation> current() {
        return Optional.ofNullable(CURRENT_OPERATION.get());
    }

    public static void clear() {
        CURRENT_OPERATION.remove();
    }

    public static final class Scope implements AutoCloseable {

        private final Operation previousOperation;

        private Scope(Operation previousOperation) {
            this.previousOperation = previousOperation;
        }

        @Override
        public void close() {
            if (previousOperation == null) {
                CURRENT_OPERATION.remove();
            } else {
                CURRENT_OPERATION.set(previousOperation);
            }
        }
    }
}