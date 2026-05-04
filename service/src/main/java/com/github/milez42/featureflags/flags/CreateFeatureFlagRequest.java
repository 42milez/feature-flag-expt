package com.github.milez42.featureflags.flags;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record CreateFeatureFlagRequest(
    @NotBlank String flagKey,
    @NotNull FeatureFlagStatus status,
    Set<@NotBlank String> targetEnvironments,
    Boolean killSwitchActive,
    Set<@NotBlank String> tenantAllowlist,
    @NotNull @Min(0) @Max(100) Integer rolloutPercentage) {}
