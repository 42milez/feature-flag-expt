package com.github.milez42.featureflags.flags;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

@Schema(description = "Feature flag representation.")
public record FeatureFlagResponse(
    @Schema(description = "Stable feature flag key.", example = "checkout-redesign") String flagKey,
    @Schema(description = "Current flag status.", example = "ENABLED") FeatureFlagStatus status,
    @Schema(description = "Target environments.", example = "[\"production\", \"staging\"]")
        Set<String> targetEnvironments,
    @Schema(description = "Whether the emergency kill switch is active.", example = "false")
        boolean killSwitchActive,
    @Schema(description = "Tenant IDs that are always allowed.", example = "[\"tenant-a\"]")
        Set<String> tenantAllowlist,
    @Schema(description = "Percentage rollout from 0 to 100.", example = "25")
        int rolloutPercentage) {}
