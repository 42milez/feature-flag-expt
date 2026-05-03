package com.github.milez42.featureflags.flags;

import java.util.Objects;

public record EvaluationResult(
        boolean enabled,
        EvaluationReason reason,
        Integer bucket
) {
    public EvaluationResult {
        Objects.requireNonNull(reason, "reason must not be null");

        if (bucket != null && (bucket < 0 || bucket > 99)) {
            throw new IllegalArgumentException("bucket must be between 0 and 99");
        }
    }
}
