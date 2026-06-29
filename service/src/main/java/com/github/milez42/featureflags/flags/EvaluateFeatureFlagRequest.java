package com.github.milez42.featureflags.flags;

import static com.github.milez42.featureflags.flags.FeatureFlagConstraints.FLAG_KEY_MAX_LENGTH;
import static com.github.milez42.featureflags.flags.FeatureFlagConstraints.FLAG_KEY_MIN_LENGTH;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to evaluate a feature flag for a context.")
public record EvaluateFeatureFlagRequest(
    @Schema(
            description = "Feature flag key to evaluate.",
            example = "checkout-redesign",
            minLength = FLAG_KEY_MIN_LENGTH)
        @NotBlank
        @Size(max = FLAG_KEY_MAX_LENGTH)
        String flagKey,
    @Schema(description = "Runtime environment.", example = "production", minLength = 1)
        @NotBlank
        @Size(max = 255)
        String environment,
    @Schema(description = "Optional tenant identifier.", example = "tenant-a") @Size(max = 255)
        String tenantId,
    @Schema(description = "Optional user identifier.", example = "user-123") @Size(max = 255)
        String userId) {}
