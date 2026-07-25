package io.nikitoo0os;

import java.time.Duration;
import java.util.Objects;

public record CancellationDecision(
        ChildCancellationPolicy policy,
        Duration gracePeriod
) {
    public CancellationDecision {
        Objects.requireNonNull(policy, "Policy must not be null");
        Objects.requireNonNull(gracePeriod, "Grace period must not be null");
        if (gracePeriod.isNegative()) {
            throw new IllegalArgumentException("Grace period must not be negative");
        }
    }
}
