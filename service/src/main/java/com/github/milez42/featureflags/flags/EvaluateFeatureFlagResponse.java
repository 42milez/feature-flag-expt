package com.github.milez42.featureflags.flags;

public record EvaluateFeatureFlagResponse(
        String flagKey,
        boolean enabled,
        EvaluationReason reason,
        Integer bucket
) {
}
