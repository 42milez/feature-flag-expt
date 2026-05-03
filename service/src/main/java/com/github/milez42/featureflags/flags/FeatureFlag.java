package com.github.milez42.featureflags.flags;

import java.util.Objects;
import java.util.Set;

public record FeatureFlag(
        String flagKey,
        FeatureFlagStatus status,
        Set<String> targetEnvironments,
        boolean killSwitchActive,
        Set<String> tenantAllowlist,
        int rolloutPercentage
) {
    public FeatureFlag {
        Objects.requireNonNull(flagKey, "flagKey must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(targetEnvironments, "targetEnvironments must not be null");
        Objects.requireNonNull(tenantAllowlist, "tenantAllowlist must not be null");

        if (flagKey.isBlank()) {
            throw new IllegalArgumentException("flagKey must not be blank");
        }
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException("rolloutPercentage must be between 0 and 100");
        }

        targetEnvironments = Set.copyOf(targetEnvironments);
        tenantAllowlist = Set.copyOf(tenantAllowlist);
    }
}
