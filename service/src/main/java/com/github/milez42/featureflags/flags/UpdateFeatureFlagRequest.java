package com.github.milez42.featureflags.flags;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(description = "Request to partially update a feature flag.")
public record UpdateFeatureFlagRequest(
    @Schema(description = "Updated flag status.", example = "DISABLED") FeatureFlagStatus status,
    @Schema(
            description =
                "Replacement target environments. Empty input clears target environments.",
            example = "[\"production\"]")
        Set<Environment> targetEnvironments,
    @Schema(description = "Updated kill switch state.", example = "true") Boolean killSwitchActive,
    @Schema(
            description = "Replacement tenant allowlist. Empty input clears the allowlist.",
            example = "[\"tenant-a\", \"tenant-b\"]")
        @Size(max = 1000)
        Set<@NotBlank @Size(max = 255) String> tenantAllowlist,
    @Schema(description = "Updated percentage rollout from 0 to 100.", example = "50")
        @Min(0)
        @Max(100)
        Integer rolloutPercentage) {}
