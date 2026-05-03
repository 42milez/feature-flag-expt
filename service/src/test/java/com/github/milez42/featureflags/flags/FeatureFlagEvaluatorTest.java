package com.github.milez42.featureflags.flags;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagEvaluatorTest {
    private final FeatureFlagEvaluator evaluator = new FeatureFlagEvaluator();

    @Test
    void disabledFlagIsAlwaysDisabled() {
        FeatureFlag flag = flag(FeatureFlagStatus.DISABLED, false, Set.of(), 100);

        EvaluationResult result = evaluator.evaluate(flag, context("production", "tenant-a", "user-a"));

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.FLAG_DISABLED);
        assertThat(result.bucket()).isNull();
    }

    @Test
    void environmentNotTargetedDisablesFlag() {
        FeatureFlag flag = flag(FeatureFlagStatus.ENABLED, false, Set.of(), 100);

        EvaluationResult result = evaluator.evaluate(flag, context("staging", "tenant-a", "user-a"));

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.ENVIRONMENT_NOT_TARGETED);
        assertThat(result.bucket()).isNull();
    }

    @Test
    void killSwitchActiveDisablesFlag() {
        FeatureFlag flag = flag(FeatureFlagStatus.ENABLED, true, Set.of(), 100);

        EvaluationResult result = evaluator.evaluate(flag, context("production", "tenant-a", "user-a"));

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.KILL_SWITCH_ACTIVE);
        assertThat(result.bucket()).isNull();
    }

    @Test
    void killSwitchTakesPriorityOverTenantAllowlist() {
        FeatureFlag flag = flag(FeatureFlagStatus.ENABLED, true, Set.of("tenant-a"), 100);

        EvaluationResult result = evaluator.evaluate(flag, context("production", "tenant-a", "user-a"));

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.KILL_SWITCH_ACTIVE);
        assertThat(result.bucket()).isNull();
    }

    @Test
    void tenantAllowlistMatchEnablesFlag() {
        FeatureFlag flag = flag(FeatureFlagStatus.ENABLED, false, Set.of("tenant-a"), 0);

        EvaluationResult result = evaluator.evaluate(flag, context("production", "tenant-a", "user-a"));

        assertThat(result.enabled()).isTrue();
        assertThat(result.reason()).isEqualTo(EvaluationReason.TENANT_ALLOWLIST_MATCH);
        assertThat(result.bucket()).isNull();
    }

    @Test
    void zeroPercentRolloutDisablesFlag() {
        FeatureFlag flag = flag(FeatureFlagStatus.ENABLED, false, Set.of(), 0);

        EvaluationResult result = evaluator.evaluate(flag, context("production", "tenant-a", "user-a"));

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.ROLLOUT_MISS);
        assertThat(result.bucket()).isBetween(0, 99);
    }

    @Test
    void fullRolloutEnablesFlag() {
        FeatureFlag flag = flag(FeatureFlagStatus.ENABLED, false, Set.of(), 100);

        EvaluationResult result = evaluator.evaluate(flag, context("production", "tenant-a", "user-a"));

        assertThat(result.enabled()).isTrue();
        assertThat(result.reason()).isEqualTo(EvaluationReason.ROLLOUT_MATCH);
        assertThat(result.bucket()).isBetween(0, 99);
    }

    @Test
    void sameFlagKeyAndTenantIdProduceDeterministicBucket() {
        FeatureFlag flag = flag(FeatureFlagStatus.ENABLED, false, Set.of(), 50);
        EvaluationContext context = context("production", "tenant-a", "user-a");

        EvaluationResult first = evaluator.evaluate(flag, context);
        EvaluationResult second = evaluator.evaluate(flag, context);

        assertThat(first.bucket()).isEqualTo(second.bucket());
        assertThat(first.enabled()).isEqualTo(second.enabled());
        assertThat(first.reason()).isEqualTo(second.reason());
    }

    @Test
    void tenantIdTakesPriorityOverUserIdForBucketCalculation() {
        FeatureFlag flag = flag(FeatureFlagStatus.ENABLED, false, Set.of(), 50);

        EvaluationResult withFirstUser = evaluator.evaluate(flag, context("production", "tenant-a", "user-a"));
        EvaluationResult withSecondUser = evaluator.evaluate(flag, context("production", "tenant-a", "user-b"));
        EvaluationResult withTenantOnly = evaluator.evaluate(flag, context("production", "tenant-a", null));

        assertThat(withFirstUser.bucket()).isEqualTo(withSecondUser.bucket());
        assertThat(withFirstUser.bucket()).isEqualTo(withTenantOnly.bucket());
    }

    @Test
    void userIdIsUsedForBucketCalculationWhenTenantIdIsMissing() {
        FeatureFlag flag = flag(FeatureFlagStatus.ENABLED, false, Set.of(), 50);

        EvaluationResult withUserOnly = evaluator.evaluate(flag, context("production", null, "user-a"));
        EvaluationResult withSameValueAsTenant = evaluator.evaluate(flag, context("production", "user-a", "ignored-user"));

        assertThat(withUserOnly.bucket()).isEqualTo(withSameValueAsTenant.bucket());
    }

    @Test
    void missingRolloutIdentityDisablesFlagWithoutBucket() {
        FeatureFlag flag = flag(FeatureFlagStatus.ENABLED, false, Set.of(), 100);

        EvaluationResult result = evaluator.evaluate(flag, context("production", null, null));

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.ROLLOUT_MISS);
        assertThat(result.bucket()).isNull();
    }

    private FeatureFlag flag(
            FeatureFlagStatus status,
            boolean killSwitchActive,
            Set<String> tenantAllowlist,
            int rolloutPercentage
    ) {
        return new FeatureFlag(
                "checkout-redesign",
                status,
                Set.of("production"),
                killSwitchActive,
                tenantAllowlist,
                rolloutPercentage
        );
    }

    private EvaluationContext context(String environment, String tenantId, String userId) {
        return new EvaluationContext(environment, tenantId, userId);
    }
}
