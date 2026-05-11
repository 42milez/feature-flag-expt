package com.github.milez42.featureflags.flags;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to evaluate a feature flag for a context.")
public record EvaluateFeatureFlagRequest(
    @Schema(
            description = "Feature flag key to evaluate.",
            example = "checkout-redesign",
            minLength = 1)
        @NotBlank
        @Size(max = 200)
        String flagKey,
    @Schema(description = "Runtime environment.", example = "production", minLength = 1)
        @NotBlank
        @Size(max = 255)
        String environment,
    @Schema(description = "Optional tenant identifier.", example = "tenant-a") @Size(max = 255)
        String tenantId,
    @Schema(description = "Optional user identifier.", example = "user-123") @Size(max = 255)
        String userId) {}
