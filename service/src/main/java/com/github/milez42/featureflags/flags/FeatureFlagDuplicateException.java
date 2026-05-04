package com.github.milez42.featureflags.flags;

public class FeatureFlagDuplicateException extends RuntimeException {
    public FeatureFlagDuplicateException(String flagKey) {
        super("Feature flag already exists: " + flagKey);
    }
}
