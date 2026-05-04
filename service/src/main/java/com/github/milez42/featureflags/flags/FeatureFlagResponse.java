package com.github.milez42.featureflags.flags;

import java.util.Set;

public record FeatureFlagResponse(
        String flagKey,
        FeatureFlagStatus status,
        Set<String> targetEnvironments,
        boolean killSwitchActive,
        Set<String> tenantAllowlist,
        int rolloutPercentage
) {
}
