package com.github.milez42.featureflags.flags;

import java.util.Objects;

public record EvaluationContext(
        String environment,
        String tenantId,
        String userId
) {
    public EvaluationContext {
        Objects.requireNonNull(environment, "environment must not be null");

        if (environment.isBlank()) {
            throw new IllegalArgumentException("environment must not be blank");
        }
    }
}
