import io.nikitoo0os.GhostWork;
import io.nikitoo0os.OperationMetadata;
import io.nikitoo0os.entity.enums.OperationState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationHandleTest {

    @Test
    void handleShouldBindTasksAndCompleteIdempotently() throws Exception {
        GhostWork ghostWork = GhostWork.create(
                Executors.newSingleThreadExecutor()
        );
        TestMetadata metadata = new TestMetadata("request-1");
        var handle = ghostWork.startOperation("GET /orders/{id}", metadata);

        try (var ignored = handle.openScope()) {
            ghostWork.executor()
                    .submit("LoadOrder", () -> {
                    })
                    .get();
        }

        assertTrue(handle.complete());
        assertFalse(handle.abort(null));
        assertEquals(OperationState.COMPLETED, handle.view().state());
        assertEquals(
                metadata,
                ghostWork.operationDetails(handle.id()).metadata()
        );
        assertEquals(
                1,
                ghostWork.operationDetails(handle.id()).tasks().size()
        );
        assertFalse(
                ghostWork.operationDetails(handle.id()).timeline().isEmpty()
        );
        ghostWork.executor().shutdownNow();
    }

    @Test
    void handleShouldSupportAbortedState() {
        GhostWork ghostWork = GhostWork.create(
                Executors.newSingleThreadExecutor()
        );
        var handle = ghostWork.startOperation(
                "GET /stream",
                new TestMetadata("request-2")
        );

        assertTrue(handle.abort(new java.io.IOException("client disconnected")));
        assertEquals(OperationState.ABORTED, handle.view().state());
        ghostWork.executor().shutdownNow();
    }

    private record TestMetadata(String requestId)
            implements OperationMetadata {
    }
}
