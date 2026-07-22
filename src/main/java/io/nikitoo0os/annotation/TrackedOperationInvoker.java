package io.nikitoo0os.annotation;

import io.nikitoo0os.GhostWork;
import io.nikitoo0os.operation.OperationDefinition;

import java.lang.reflect.InvocationTargetException;
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

    public Object invoke(
            Object target,
            Method method,
            Object[] args
    ) throws Exception {
        Objects.requireNonNull(target, "Target must not be null");
        Objects.requireNonNull(method, "Method must not be null");

        OperationDefinition definition =
                resolveRequired(method);

        return ghostWork.call(
                definition,
                () -> invokeMethod(target, method, args)
        );
    }

    private OperationDefinition resolveRequired(Method method) {
        Optional<OperationDefinition> definition =
                resolver.resolve(method);

        return definition.orElseThrow(() -> new IllegalStateException(
                "Method is not annotated with @TrackedOperation"
        ));
    }

    private Object invokeMethod(
            Object target,
            Method method,
            Object[] args
    ) throws Exception {
        try {
            Object[] invocationArgs = args == null
                    ? new Object[0]
                    : args;

            if (!method.canAccess(target)) {
                method.setAccessible(true);
            }

            return method.invoke(target, invocationArgs);
        } catch (InvocationTargetException exception) {
            Throwable targetFailure = exception.getTargetException();

            if (targetFailure instanceof Exception checkedException) {
                throw checkedException;
            }

            if (targetFailure instanceof Error error) {
                throw error;
            }

            throw new RuntimeException(targetFailure);
        }
    }
}