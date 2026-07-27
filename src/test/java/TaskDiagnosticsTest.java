import io.nikitoo0os.ExecutorMetadata;
import io.nikitoo0os.SubmissionSource;
import io.nikitoo0os.TaskDiagnostics;
import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Task;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaskDiagnosticsTest {

    private static final Instant SUBMITTED =
            Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void queuedDiagnosticsShouldExposeQueueDurationAndExecutorMetadata() {
        ExecutorMetadata executor = new ExecutorMetadata(
                "applicationTaskExecutor",
                "example.Executor",
                SubmissionSource.SPRING_EXECUTOR
        );
        Task task = new Task("LoadCustomers", new Operation("Import"), executor);
        task.submit(SUBMITTED);

        TaskDiagnostics diagnostics = TaskDiagnostics.from(
                task,
                SUBMITTED.plusSeconds(15)
        );

        assertEquals(Duration.ofSeconds(15), diagnostics.queueDuration());
        assertEquals(Duration.ZERO, diagnostics.executionDuration());
        assertEquals(executor, diagnostics.executionMetadata().executor());
        assertNull(diagnostics.executionMetadata().thread());
        assertNotNull(diagnostics.executionMetadata().source());
    }

    @Test
    void startedTaskShouldCaptureCurrentThreadMetadata() {
        Task task = new Task(
                "LoadCustomers",
                new Operation("Import"),
                ExecutorMetadata.manual(getClass())
        );
        task.submit(SUBMITTED);
        task.start(SUBMITTED.plusSeconds(5));

        TaskDiagnostics diagnostics = TaskDiagnostics.from(
                task,
                SUBMITTED.plusSeconds(12)
        );

        assertEquals(Duration.ofSeconds(5), diagnostics.queueDuration());
        assertEquals(Duration.ofSeconds(7), diagnostics.executionDuration());
        assertEquals(
                Thread.currentThread().getName(),
                diagnostics.executionMetadata().thread().name()
        );
        assertEquals(
                Thread.currentThread().threadId(),
                diagnostics.executionMetadata().thread().id()
        );
        assertFalse(diagnostics.executionMetadata().thread().virtual());
    }
}
