package io.nikitoo0os;

import java.util.List;
import java.util.Objects;

public record TaskSourceMetadata(
        CodeLocation callSite,
        List<CodeLocation> callTrace
) {
    private static final int MAX_TRACE_DEPTH = 8;
    private static final StackWalker STACK_WALKER = StackWalker.getInstance();

    public TaskSourceMetadata {
        callSite = Objects.requireNonNull(
                callSite,
                "Call site must not be null"
        );
        callTrace = List.copyOf(
                Objects.requireNonNull(
                        callTrace,
                        "Call trace must not be null"
                )
        );
        if (callTrace.isEmpty()) {
            throw new IllegalArgumentException("Call trace must not be empty");
        }
        if (!callSite.equals(callTrace.getFirst())) {
            throw new IllegalArgumentException(
                    "Call site must be the first call trace frame"
            );
        }
    }

    public static TaskSourceMetadata capture() {
        List<CodeLocation> trace = STACK_WALKER.walk(frames ->
                frames
                        .filter(frame -> isApplicationFrame(
                                frame.getClassName()
                        ))
                        .limit(MAX_TRACE_DEPTH)
                        .map(CodeLocation::from)
                        .toList()
        );

        if (trace.isEmpty()) {
            CodeLocation unavailable = new CodeLocation(
                    "unknown",
                    "unknown",
                    null,
                    null
            );
            return new TaskSourceMetadata(
                    unavailable,
                    List.of(unavailable)
            );
        }
        return new TaskSourceMetadata(trace.getFirst(), trace);
    }

    private static boolean isApplicationFrame(String className) {
        return !isGhostWorkInfrastructureFrame(className)
                && !className.startsWith("java.")
                && !className.startsWith("jdk.")
                && !className.startsWith("sun.")
                && !className.startsWith("org.springframework.")
                && !className.startsWith("org.aspectj.")
                && !className.startsWith("org.junit.")
                && !className.startsWith("org.apache.maven.");
    }

    private static boolean isGhostWorkInfrastructureFrame(String className) {
        return className.startsWith("io.nikitoo0os.factory.")
                || className.startsWith("io.nikitoo0os.wrap.")
                || className.startsWith("io.nikitoo0os.entity.")
                || className.startsWith("io.nikitoo0os.context.")
                || className.startsWith("io.nikitoo0os.runner.")
                || className.startsWith("io.nikitoo0os.scheduling.")
                || className.startsWith("io.nikitoo0os.spring.")
                || className.equals("io.nikitoo0os.GhostWork")
                || className.equals(
                        "io.nikitoo0os.TrackingExecutorService"
                )
                || className.equals(
                        "io.nikitoo0os.TaskExecutionMetadata"
                )
                || className.equals("io.nikitoo0os.TaskSourceMetadata");
    }
}
