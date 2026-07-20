package io.nikitoo0os.annotation;

import io.nikitoo0os.GhostWork;
import io.nikitoo0os.operation.OperationDefinition;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

public final class TrackedOperationInvoker {

    private final GhostWork ghostWork;
    private final TrackedOperationResolver resolver;

    public TrackedOperationInvoker(
            GhostWork ghostWork,
            TrackedOperationResolver resolver
    ) {
        this.ghostWork = Objects.requireNonNull(
                ghostWork,
                "GhostWork must not be null"
        );
        this.resolver = Objects.requireNonNull(
                resolver,
                "Tracked operation resolver must not be null"
        );
    }

    public void run(Method method, Runnable runnable) {
        Objects.requireNonNull(runnable, "Runnable must not be null");

        OperationDefinition definition =
                resolveRequired(method);

        ghostWork.run(definition, runnable);
    }

    public <T> T call(
            Method method,
            Callable<T> callable
    ) throws Exception {
        Objects.requireNonNull(callable, "Callable must not be null");

        OperationDefinition definition =
                resolveRequired(method);

        return ghostWork.call(definition, callable);
    }

    private OperationDefinition resolveRequired(Method method) {
        Optional<OperationDefinition> definition =
                resolver.resolve(method);

        return definition.orElseThrow(() -> new IllegalStateException(
                "Method is not annotated with @TrackedOperation"
        ));
    }
}