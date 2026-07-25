package io.nikitoo0os;

import java.util.Objects;

public record ExecutorMetadata(
        String beanName,
        String implementationClass,
        SubmissionSource submissionSource
) {
    public ExecutorMetadata {
        implementationClass = Objects.requireNonNull(
                implementationClass,
                "Executor implementation class must not be null"
        );
        submissionSource = Objects.requireNonNull(
                submissionSource,
                "Submission source must not be null"
        );
    }

    public static ExecutorMetadata manual(Class<?> executorClass) {
        Objects.requireNonNull(executorClass, "Executor class must not be null");
        return new ExecutorMetadata(
                null,
                executorClass.getName(),
                SubmissionSource.MANUAL_API
        );
    }
}
