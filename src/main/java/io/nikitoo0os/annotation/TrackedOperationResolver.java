package io.nikitoo0os.annotation;

import io.nikitoo0os.operation.OperationDefinition;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

public final class TrackedOperationResolver {

    public Optional<OperationDefinition> resolve(Method method) {
        Objects.requireNonNull(method, "Method must not be null");

        TrackedOperation annotation =
                method.getAnnotation(TrackedOperation.class);

        if (annotation == null) {
            return Optional.empty();
        }

        return Optional.of(
                OperationDefinition.named(annotation.value())
        );
    }
}