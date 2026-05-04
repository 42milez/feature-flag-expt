package com.github.milez42.featureflags.flags;

import jakarta.validation.constraints.NotBlank;

public record EvaluateFeatureFlagRequest(
    @NotBlank String flagKey, @NotBlank String environment, String tenantId, String userId) {}
