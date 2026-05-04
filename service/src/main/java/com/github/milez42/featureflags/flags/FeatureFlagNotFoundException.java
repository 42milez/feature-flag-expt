package com.github.milez42.featureflags.flags;

public class FeatureFlagNotFoundException extends RuntimeException {
    public FeatureFlagNotFoundException(String flagKey) {
        super("Feature flag not found: " + flagKey);
    }
}
