import io.nikitoo0os.CodeLocation;
import io.nikitoo0os.TaskSourceMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskSourceMetadataTest {

    @Test
    void captureShouldExposeApplicationCallSiteAndBoundedTrace() {
        TaskSourceMetadata metadata = captureFromApplicationMethod();

        CodeLocation callSite = metadata.callSite();
        assertEquals(getClass().getName(), callSite.className());
        assertEquals(
                "captureFromApplicationMethod",
                callSite.methodName()
        );
        assertEquals("TaskSourceMetadataTest.java", callSite.fileName());
        assertNotNull(callSite.lineNumber());
        assertTrue(callSite.lineNumber() > 0);
        assertFalse(metadata.callTrace().isEmpty());
        assertTrue(metadata.callTrace().size() <= 8);
        assertEquals(callSite, metadata.callTrace().getFirst());
    }

    private TaskSourceMetadata captureFromApplicationMethod() {
        return TaskSourceMetadata.capture();
    }
}
