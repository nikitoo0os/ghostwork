package io.nikitoo0os;

import java.time.Duration;
import java.util.List;

@FunctionalInterface
public interface CancellationPolicy {
    CancellationDecision decide(
            OperationView operation,
            CancellationTrigger trigger,
            List<TaskView> activeTasks
    );

    static CancellationPolicy conservativeDefaults() {
        return (operation, trigger, activeTasks) -> new CancellationDecision(
                switch (trigger) {
                    case OPERATION_TIMEOUT, CLIENT_ABORT,
                            APPLICATION_SHUTDOWN ->
                            ChildCancellationPolicy.REQUEST_CANCELLATION;
                    case OPERATION_CANCEL ->
                            ChildCancellationPolicy.CANCEL_ALL;
                    case OPERATION_FAILURE, OPERATION_COMPLETE ->
                            ChildCancellationPolicy.NONE;
                    case PARENT_TASK_CANCEL ->
                            ChildCancellationPolicy.REQUEST_CANCELLATION;
                },
                Duration.ofSeconds(5)
        );
    }
}
