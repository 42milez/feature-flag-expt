package com.github.milez42.featureflags.flags;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record UpdateFeatureFlagRequest(
        FeatureFlagStatus status,
        Set<@NotBlank String> targetEnvironments,
        Boolean killSwitchActive,
        Set<@NotBlank String> tenantAllowlist,
        @Min(0) @Max(100) Integer rolloutPercentage
) {
}
