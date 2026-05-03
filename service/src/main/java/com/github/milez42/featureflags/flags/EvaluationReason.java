package com.github.milez42.featureflags.flags;

public enum EvaluationReason {
    FLAG_DISABLED,
    ENVIRONMENT_NOT_TARGETED,
    KILL_SWITCH_ACTIVE,
    TENANT_ALLOWLIST_MATCH,
    ROLLOUT_MATCH,
    ROLLOUT_MISS
}
