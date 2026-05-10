package com.github.milez42.featureflags.flags;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Feature flag evaluation result.")
public record EvaluateFeatureFlagResponse(
    @Schema(description = "Evaluated feature flag key.", example = "checkout-redesign")
        String flagKey,
    @Schema(description = "Whether the flag is enabled for the supplied context.", example = "true")
        boolean enabled,
    @Schema(description = "Reason for the evaluation result.", example = "ROLLOUT_MATCH")
        EvaluationReason reason,
    @Schema(
            description = "Deterministic rollout bucket, when rollout logic was evaluated.",
            example = "42")
        Integer bucket) {}
