package io.nikitoo0os.operation;

import java.util.Objects;

public record OperationDefinition(String name) {

    public OperationDefinition {
        Objects.requireNonNull(name, "Operation name must not be null");

        if (name.trim().isBlank()) {
            throw new IllegalArgumentException(
                    "Operation name must not be blank"
            );
        }
    }

    public static OperationDefinition named(String name) {
        return new OperationDefinition(name);
    }
}