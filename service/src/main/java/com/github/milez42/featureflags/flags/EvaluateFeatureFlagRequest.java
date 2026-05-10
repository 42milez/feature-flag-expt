package com.github.milez42.featureflags.flags;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to evaluate a feature flag for a context.")
public record EvaluateFeatureFlagRequest(
    @Schema(description = "Feature flag key to evaluate.", example = "checkout-redesign") @NotBlank
        String flagKey,
    @Schema(description = "Runtime environment.", example = "production") @NotBlank
        String environment,
    @Schema(description = "Optional tenant identifier.", example = "tenant-a") String tenantId,
    @Schema(description = "Optional user identifier.", example = "user-123") String userId) {}
