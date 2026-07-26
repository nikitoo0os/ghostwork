package io.nikitoo0os;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record CorrelationId(String value) {
    public static final int MAX_LENGTH = 128;
    private static final Pattern SAFE_VALUE = Pattern.compile(
            "[A-Za-z0-9._:-]+"
    );

    public CorrelationId {
        Objects.requireNonNull(value, "Correlation id must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Correlation id must not be blank"
            );
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Correlation id must not exceed " + MAX_LENGTH
                            + " characters"
            );
        }
        if (!SAFE_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Correlation id contains unsupported characters"
            );
        }
    }

    public static CorrelationId random() {
        return new CorrelationId(UUID.randomUUID().toString());
    }
}
