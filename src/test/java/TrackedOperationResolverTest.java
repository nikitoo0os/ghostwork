import io.nikitoo0os.annotation.TrackedOperation;
import io.nikitoo0os.annotation.TrackedOperationResolver;
import io.nikitoo0os.operation.OperationDefinition;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TrackedOperationResolverTest {

    private final TrackedOperationResolver resolver =
            new TrackedOperationResolver();

    @Test
    void resolveShouldReturnEmptyWhenAnnotationMissing()
            throws NoSuchMethodException {
        Method method = TestService.class.getDeclaredMethod(
                "notAnnotated"
        );

        assertEquals(Optional.empty(), resolver.resolve(method));
    }

    @Test
    void resolveShouldReturnDefinitionForAnnotatedMethod()
            throws NoSuchMethodException {
        Method method = TestService.class.getDeclaredMethod(
                "importUsers"
        );

        Optional<OperationDefinition> definition =
                resolver.resolve(method);

        assertTrue(definition.isPresent());
        assertEquals("ImportUsers", definition.get().name());
    }

    @Test
    void resolveShouldRejectNullMethod() {
        assertThrows(
                NullPointerException.class,
                () -> resolver.resolve(null)
        );
    }

    @Test
    void resolveShouldRejectBlankAnnotationValue()
            throws NoSuchMethodException {
        Method method = TestService.class.getDeclaredMethod(
                "blankName"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(method)
        );
    }

    private static final class TestService {

        void notAnnotated() {
        }

        @TrackedOperation("ImportUsers")
        void importUsers() {
        }

        @TrackedOperation("   ")
        void blankName() {
        }
    }
}