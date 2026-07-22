import io.nikitoo0os.entity.TaskSnapshot;
import io.nikitoo0os.entity.enums.TaskState;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class TaskSnapshotTest {

    private static final Instant STARTED_AT =
            Instant.parse("2026-01-01T10:00:00Z");

    private static final Instant FINISHED_AT =
            Instant.parse("2026-01-01T10:00:10Z");

    @Test
    void createdSnapshotShouldHaveNoTimes() {
        TaskSnapshot snapshot =
                new TaskSnapshot(null, null, TaskState.CREATED);

        assertEquals(TaskState.CREATED, snapshot.getState());
        assertNull(snapshot.getStartedAt());
        assertNull(snapshot.getFinishedAt());

        assertThrows(
                IllegalStateException.class,
                () -> new TaskSnapshot(
                        STARTED_AT,
                        null,
                        TaskState.CREATED
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> new TaskSnapshot(
                        null,
                        FINISHED_AT,
                        TaskState.CREATED
                )
        );
    }

    @Test
    void runningSnapshotShouldHaveStartTimeOnly() {
        TaskSnapshot snapshot =
                new TaskSnapshot(STARTED_AT, null, TaskState.RUNNING);

        assertEquals(TaskState.RUNNING, snapshot.getState());
        assertEquals(STARTED_AT, snapshot.getStartedAt());
        assertNull(snapshot.getFinishedAt());

        assertThrows(
                IllegalStateException.class,
                () -> new TaskSnapshot(
                        null,
                        null,
                        TaskState.RUNNING
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> new TaskSnapshot(
                        STARTED_AT,
                        FINISHED_AT,
                        TaskState.RUNNING
                )
        );
    }

    @Test
    void completedSnapshotShouldHaveStartAndFinishTimes() {
        assertFinishedAfterRunSnapshot(TaskState.COMPLETED);
    }

    @Test
    void failedSnapshotShouldHaveStartAndFinishTimes() {
        assertFinishedAfterRunSnapshot(TaskState.FAILED);
    }

    @Test
    void cancelledSnapshotShouldHaveFinishTimeAndOptionalStartTime() {
        TaskSnapshot cancelledBeforeStart =
                new TaskSnapshot(null, FINISHED_AT, TaskState.CANCELLED);

        assertEquals(TaskState.CANCELLED, cancelledBeforeStart.getState());
        assertNull(cancelledBeforeStart.getStartedAt());
        assertEquals(FINISHED_AT, cancelledBeforeStart.getFinishedAt());

        TaskSnapshot cancelledAfterStart =
                new TaskSnapshot(STARTED_AT, FINISHED_AT, TaskState.CANCELLED);

        assertEquals(TaskState.CANCELLED, cancelledAfterStart.getState());
        assertEquals(STARTED_AT, cancelledAfterStart.getStartedAt());
        assertEquals(FINISHED_AT, cancelledAfterStart.getFinishedAt());

        assertThrows(
                IllegalStateException.class,
                () -> new TaskSnapshot(STARTED_AT, null, TaskState.CANCELLED)
        );
    }

    @Test
    void rejectedSnapshotShouldHaveFinishTimeOnly() {
        TaskSnapshot snapshot =
                new TaskSnapshot(null, FINISHED_AT, TaskState.REJECTED);

        assertEquals(TaskState.REJECTED, snapshot.getState());
        assertNull(snapshot.getStartedAt());
        assertEquals(FINISHED_AT, snapshot.getFinishedAt());

        assertThrows(
                IllegalStateException.class,
                () -> new TaskSnapshot(
                        STARTED_AT,
                        FINISHED_AT,
                        TaskState.REJECTED
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> new TaskSnapshot(
                        null,
                        null,
                        TaskState.REJECTED
                )
        );
    }

    @Test
    void snapshotShouldRejectNullState() {
        assertThrows(
                NullPointerException.class,
                () -> new TaskSnapshot(null, null, null)
        );
    }

    private static void assertFinishedAfterRunSnapshot(TaskState state) {
        TaskSnapshot snapshot =
                new TaskSnapshot(STARTED_AT, FINISHED_AT, state);

        assertEquals(state, snapshot.getState());
        assertEquals(STARTED_AT, snapshot.getStartedAt());
        assertEquals(FINISHED_AT, snapshot.getFinishedAt());

        assertThrows(
                IllegalStateException.class,
                () -> new TaskSnapshot(null, FINISHED_AT, state)
        );

        assertThrows(
                IllegalStateException.class,
                () -> new TaskSnapshot(STARTED_AT, null, state)
        );
    }
}
