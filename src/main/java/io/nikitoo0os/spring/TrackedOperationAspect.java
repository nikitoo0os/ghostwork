package io.nikitoo0os.spring;

import io.nikitoo0os.annotation.TrackedOperationInvoker;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Objects;

@Aspect
public final class TrackedOperationAspect {
    private final TrackedOperationInvoker invoker;

    public TrackedOperationAspect(TrackedOperationInvoker invoker) {
        this.invoker = Objects.requireNonNull(
                invoker,
                "Tracked operation invoker must not be null"
        );
    }

    @Around("@annotation(io.nikitoo0os.annotation.TrackedOperation)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Objects.requireNonNull(joinPoint, "Join point must not be null");

        Method method = resolveMethod(joinPoint);

        return invoker.invoke(
                joinPoint.getTarget(),
                method,
                joinPoint.getArgs()
        );
    }

    private Method resolveMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature =
                (MethodSignature) joinPoint.getSignature();

        Method signatureMethod = signature.getMethod();
        Object target = joinPoint.getTarget();

        if (target == null) {
            return signatureMethod;
        }

        try {
            return target.getClass().getDeclaredMethod(
                    signatureMethod.getName(),
                    signatureMethod.getParameterTypes()
            );
        } catch (NoSuchMethodException ignored) {
            return signatureMethod;
        }
    }
}
