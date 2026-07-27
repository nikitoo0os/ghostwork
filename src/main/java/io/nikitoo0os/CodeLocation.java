package io.nikitoo0os;

import java.util.Objects;

public record CodeLocation(
        String className,
        String methodName,
        String fileName,
        Integer lineNumber
) {
    public CodeLocation {
        className = Objects.requireNonNull(
                className,
                "Class name must not be null"
        );
        methodName = Objects.requireNonNull(
                methodName,
                "Method name must not be null"
        );
    }

    static CodeLocation from(StackWalker.StackFrame frame) {
        return new CodeLocation(
                frame.getClassName(),
                frame.getMethodName(),
                frame.getFileName(),
                frame.getLineNumber() < 0 ? null : frame.getLineNumber()
        );
    }
}
