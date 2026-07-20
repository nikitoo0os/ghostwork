import io.nikitoo0os.operation.OperationDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OperationDefinitionTest {

    @Test
    void namedShouldCreateDefinitionWithName() {
        OperationDefinition definition =
                OperationDefinition.named("ImportUsers");

        assertEquals("ImportUsers", definition.name());
    }

    @Test
    void definitionShouldRejectNullName() {
        assertThrows(
                NullPointerException.class,
                () -> OperationDefinition.named(null)
        );
    }

    @Test
    void definitionShouldRejectBlankName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationDefinition.named("   ")
        );
    }
}