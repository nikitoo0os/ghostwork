package io.nikitoo0os;

import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.enums.OperationState;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OperationView(
        UUID id,
        String name,
        CorrelationId correlationId,
        OperationState state,
        Instant startedAt,
        Instant finishedAt
) {
    public OperationView(
            UUID id,
            String name,
            OperationState state,
            Instant startedAt,
            Instant finishedAt
    ) {
        this(
                id,
                name,
                CorrelationId.random(),
                state,
                startedAt,
                finishedAt
        );
    }

    public static OperationView from(Operation operation) {
        Objects.requireNonNull(operation, "Operation must not be null");

        return new OperationView(
                operation.getId(),
                operation.getName(),
                operation.getCorrelationId(),
                operation.getState(),
                operation.getStartedAt(),
                operation.getFinishedAt()
        );
    }
}
