import io.nikitoo0os.GhostWork;
import io.nikitoo0os.OperationView;
import io.nikitoo0os.annotation.TrackedOperation;
import io.nikitoo0os.annotation.TrackedOperationInvoker;
import io.nikitoo0os.annotation.TrackedOperationResolver;
import io.nikitoo0os.entity.enums.OperationState;
import io.nikitoo0os.spring.TrackedOperationAspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.reflect.SourceLocation;
import org.aspectj.runtime.internal.AroundClosure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TrackedOperationAspectTest {

    private final ExecutorService executor =
            Executors.newFixedThreadPool(2);

    private final GhostWork ghostWork =
            GhostWork.create(executor);

    private final TrackedOperationAspect aspect =
            new TrackedOperationAspect(
                    new TrackedOperationInvoker(
                            ghostWork,
                            new TrackedOperationResolver()
                    )
            );

    @AfterEach
    void tearDown() {
        ghostWork.executor().shutdownNow();
    }

    @Test
    void aroundShouldInvokeAnnotatedMethodThroughTrackedOperationInvoker()
            throws Throwable {
        TestService service = new TestService();

        Method method = TestService.class.getDeclaredMethod(
                "importUsers"
        );

        Object result = aspect.around(
                new TestProceedingJoinPoint(
                        service,
                        method,
                        new Object[0]
                )
        );

        assertEquals("done", result);

        OperationView operation =
                findOperationByName("ImportUsers");

        assertEquals(OperationState.COMPLETED, operation.state());
    }

    @Test
    void constructorShouldRejectNullInvoker() {
        assertThrows(
                NullPointerException.class,
                () -> new TrackedOperationAspect(null)
        );
    }

    @Test
    void aroundShouldRejectNullJoinPoint() {
        assertThrows(
                NullPointerException.class,
                () -> aspect.around(null)
        );
    }

    private OperationView findOperationByName(String name) {
        return ghostWork.operations()
                .stream()
                .filter(operation -> operation.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static final class TestService {

        @TrackedOperation("ImportUsers")
        String importUsers() {
            return "done";
        }
    }

    private record TestProceedingJoinPoint(
            Object target,
            Method method,
            Object[] args
    ) implements ProceedingJoinPoint {

        @Override
        public Object proceed() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object proceed(Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set$AroundClosure(AroundClosure aroundClosure) {
        }

        @Override
        public Object getThis() {
            return target;
        }

        @Override
        public Object getTarget() {
            return target;
        }

        @Override
        public Object[] getArgs() {
            return args;
        }

        @Override
        public Signature getSignature() {
            return new TestMethodSignature(method);
        }

        @Override
        public SourceLocation getSourceLocation() {
            return null;
        }

        @Override
        public String getKind() {
            return "method-execution";
        }

        @Override
        public StaticPart getStaticPart() {
            return null;
        }

        @Override
        public String toShortString() {
            return method.getName();
        }

        @Override
        public String toLongString() {
            return method.toString();
        }
    }

    private record TestMethodSignature(Method method)
            implements MethodSignature {

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Class<?> getReturnType() {
            return method.getReturnType();
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return method.getParameterTypes();
        }

        @Override
        public String[] getParameterNames() {
            return new String[method.getParameterCount()];
        }

        @Override
        public Class<?>[] getExceptionTypes() {
            return method.getExceptionTypes();
        }

        @Override
        public Class<?> getDeclaringType() {
            return method.getDeclaringClass();
        }

        @Override
        public String getDeclaringTypeName() {
            return method.getDeclaringClass().getName();
        }

        @Override
        public int getModifiers() {
            return method.getModifiers();
        }

        @Override
        public String getName() {
            return method.getName();
        }

        @Override
        public String toShortString() {
            return method.getName();
        }

        @Override
        public String toLongString() {
            return method.toString();
        }
    }
}
