package com.github.milez42.featureflags.flags;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

@Schema(description = "Request to create a feature flag.")
public record CreateFeatureFlagRequest(
    @Schema(description = "Stable feature flag key.", example = "checkout-redesign") @NotBlank
        String flagKey,
    @Schema(description = "Initial flag status.", example = "ENABLED") @NotNull
        FeatureFlagStatus status,
    @Schema(
            description = "Environments where the flag may be evaluated.",
            example = "[\"production\", \"staging\"]")
        @NotNull
        @NotEmpty
        Set<Environment> targetEnvironments,
    @Schema(description = "Whether the emergency kill switch is active.", example = "false")
        @NotNull
        Boolean killSwitchActive,
    @Schema(description = "Tenant IDs that are always allowed.", example = "[\"tenant-a\"]")
        Set<@NotBlank String> tenantAllowlist,
    @Schema(description = "Percentage rollout from 0 to 100.", example = "25")
        @NotNull
        @Min(0)
        @Max(100)
        Integer rolloutPercentage) {}
