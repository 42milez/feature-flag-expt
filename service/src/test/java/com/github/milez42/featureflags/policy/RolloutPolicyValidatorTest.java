package com.github.milez42.featureflags.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.milez42.featureflags.flags.Environment;
import com.github.milez42.featureflags.flags.FeatureFlag;
import com.github.milez42.featureflags.flags.FeatureFlagStatus;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RolloutPolicyValidatorTest {
  private final RolloutPolicyValidator validator = new RolloutPolicyValidator();

  @Test
  void zeroToFullProductionRolloutIsRejected() {
    FeatureFlag current = flag(0, true);
    FeatureFlag proposed = flag(100, true);

    RolloutPolicyValidationResult result = validate(current, proposed);

    assertThat(result.violations())
        .extracting(RolloutPolicyViolation::code)
        .containsExactly("FULL_PRODUCTION_ROLLOUT");
    assertInvariant(result);
  }

  @Test
  void nonProductionZeroToFullRolloutIsAllowed() {
    FeatureFlag current = flag(Set.of("staging"), 0);
    FeatureFlag proposed = flag(Set.of("staging"), 100);

    RolloutPolicyValidationResult result = validate(current, proposed);

    assertThat(result.allowed()).isTrue();
    assertThat(result.violations()).isEmpty();
    assertInvariant(result);
  }

  @Test
  void partialToFullProductionRolloutIsAllowedByPhaseSixFullRolloutPolicy() {
    FeatureFlag current = flag(50, true);
    FeatureFlag proposed = flag(100, true);

    RolloutPolicyValidationResult result = validate(current, proposed);

    assertThat(result.allowed()).isTrue();
    assertThat(result.violations()).isEmpty();
    assertInvariant(result);
  }

  @Test
  void productionTargetingWithInactiveKillSwitchIsAllowed() {
    FeatureFlag current = flag(FeatureFlagStatus.DISABLED, Set.of("staging"), false);
    FeatureFlag proposed = flag(FeatureFlagStatus.DISABLED, Set.of("production"), false);

    RolloutPolicyValidationResult result = validate(current, proposed);

    assertThat(result.allowed()).isTrue();
    assertThat(result.violations()).isEmpty();
    assertInvariant(result);
  }

  @Test
  void zeroPercentProductionWithInactiveKillSwitchIsAllowed() {
    FeatureFlag current = flag(0, false);
    FeatureFlag proposed = flag(0, false);

    RolloutPolicyValidationResult result = validate(current, proposed);

    assertThat(result.allowed()).isTrue();
    assertThat(result.violations()).isEmpty();
    assertInvariant(result);
  }

  @Test
  void highRiskChangeWithoutApprovalIsRejected() {
    FeatureFlag current = flag(Set.of("staging"), 25);
    FeatureFlag proposed = flag(Set.of("staging"), 50);

    RolloutPolicyValidationResult result = validator.validate(current, proposed, highRiskContext());

    assertThat(result.violations())
        .extracting(RolloutPolicyViolation::code)
        .containsExactly("HIGH_RISK_REQUIRES_APPROVAL");
    assertInvariant(result);
  }

  @Test
  void productionEnablementWithoutAllowlistRequiresNonBlankReason() {
    FeatureFlag current =
        flag(FeatureFlagStatus.DISABLED, Set.of("production"), false, Set.of(), 0);
    FeatureFlag proposed =
        flag(FeatureFlagStatus.ENABLED, Set.of("production"), false, Set.of(), 0);

    RolloutPolicyValidationResult result =
        validator.validate(current, proposed, lowRiskContext(" "));

    assertThat(result.violations())
        .extracting(RolloutPolicyViolation::code)
        .containsExactly("PRODUCTION_ENABLEMENT_REQUIRES_REASON");
    assertInvariant(result);
  }

  @Test
  void productionEnablementWithoutAllowlistAllowsValidReason() {
    FeatureFlag current =
        flag(FeatureFlagStatus.DISABLED, Set.of("production"), false, Set.of(), 0);
    FeatureFlag proposed =
        flag(FeatureFlagStatus.ENABLED, Set.of("production"), false, Set.of(), 0);

    RolloutPolicyValidationResult result =
        validator.validate(current, proposed, lowRiskContext("valid business justification"));

    assertThat(result.allowed()).isTrue();
    assertThat(result.violations()).isEmpty();
    assertInvariant(result);
  }

  @Test
  void multipleViolationsAreReturnedTogether() {
    FeatureFlag current = flag(FeatureFlagStatus.ENABLED, Set.of("production"), false, Set.of(), 0);
    FeatureFlag proposed =
        flag(FeatureFlagStatus.ENABLED, Set.of("production"), false, Set.of(), 100);

    RolloutPolicyValidationResult result = validator.validate(current, proposed, highRiskContext());

    assertThat(result.violations())
        .extracting(RolloutPolicyViolation::code)
        .containsExactlyInAnyOrder(
            "FULL_PRODUCTION_ROLLOUT",
            "HIGH_RISK_REQUIRES_APPROVAL",
            "PRODUCTION_ENABLEMENT_REQUIRES_REASON");
    assertInvariant(result);
  }

  @Test
  void safeStagedRolloutIsAllowed() {
    FeatureFlag current = flag(Set.of("staging"), 10);
    FeatureFlag proposed = flag(Set.of("staging"), 25);

    RolloutPolicyValidationResult result = validate(current, proposed);

    assertThat(result.allowed()).isTrue();
    assertThat(result.violations()).isEmpty();
    assertInvariant(result);
  }

  @Test
  void directZeroToFullProductionUpdateReturnsFullRolloutAndHighRiskViolations() {
    FeatureFlag current =
        flag(FeatureFlagStatus.ENABLED, Set.of("production"), false, Set.of("tenant-a"), 0);
    FeatureFlag proposed =
        flag(FeatureFlagStatus.ENABLED, Set.of("production"), false, Set.of("tenant-a"), 100);

    RolloutPolicyValidationResult result = validator.validate(current, proposed, highRiskContext());

    assertThat(result.violations())
        .extracting(RolloutPolicyViolation::code)
        .containsExactly("FULL_PRODUCTION_ROLLOUT", "HIGH_RISK_REQUIRES_APPROVAL");
    assertInvariant(result);
  }

  @Test
  void stagedProductionRolloutChangesDoNotReturnFullProductionRollout() {
    FeatureFlag zero = flag(0, true);
    FeatureFlag fifty = flag(50, true);
    FeatureFlag full = flag(100, true);

    assertThat(validate(zero, fifty).violations())
        .extracting(RolloutPolicyViolation::code)
        .doesNotContain("FULL_PRODUCTION_ROLLOUT");
    assertThat(validate(fifty, full).violations())
        .extracting(RolloutPolicyViolation::code)
        .doesNotContain("FULL_PRODUCTION_ROLLOUT");
  }

  @Test
  void productionEnablementRequiresReasonEvenWhenApprovalIsVerified() {
    FeatureFlag current =
        flag(FeatureFlagStatus.DISABLED, Set.of("production"), false, Set.of(), 0);
    FeatureFlag proposed =
        flag(FeatureFlagStatus.ENABLED, Set.of("production"), false, Set.of(), 0);

    RolloutPolicyValidationResult result =
        validator.validate(
            current,
            proposed,
            new RolloutPolicyContext(
                highRiskAssessment(),
                new ApprovalState.Verified(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"), "approver"),
                null));

    assertThat(result.violations())
        .extracting(RolloutPolicyViolation::code)
        .containsExactly("PRODUCTION_ENABLEMENT_REQUIRES_REASON");
    assertInvariant(result);
  }

  @Test
  void emptyTargetEnvironmentsProposalClearsTargetEnvironments() {
    FeatureFlag current = flag(FeatureFlagStatus.DISABLED, Set.of("production"), false);
    FeatureFlag proposed = flag(FeatureFlagStatus.DISABLED, Set.of(), false);

    RolloutPolicyValidationResult result = validate(current, proposed);

    assertThat(result.allowed()).isTrue();
    assertThat(result.violations()).isEmpty();
    assertInvariant(result);
  }

  @Test
  void emptyTenantAllowlistProposalClearsTenantAllowlist() {
    FeatureFlag current = flag(Set.of("tenant-a"), false);
    FeatureFlag proposed = flag(Set.of(), false);

    RolloutPolicyValidationResult result = validate(current, proposed);

    assertThat(result.violations())
        .extracting(RolloutPolicyViolation::code)
        .contains("PRODUCTION_ENABLEMENT_REQUIRES_REASON");
    assertInvariant(result);
  }

  @Test
  void productionTargetEnvironmentWireStringWithInactiveKillSwitchIsAllowed() {
    FeatureFlag current = flag(FeatureFlagStatus.DISABLED, Set.of("staging"), false);
    FeatureFlag proposed =
        flag(FeatureFlagStatus.DISABLED, Set.of(Environment.PRODUCTION.value()), false);

    assertThat(proposed.targetEnvironments()).containsExactly("production");

    RolloutPolicyValidationResult result = validate(current, proposed);

    assertThat(result.allowed()).isTrue();
    assertThat(result.violations()).isEmpty();
    assertInvariant(result);
  }

  private RolloutPolicyValidationResult validate(FeatureFlag current, FeatureFlag proposed) {
    return validator.validate(current, proposed, lowRiskContext(null));
  }

  private RolloutPolicyContext lowRiskContext(String reason) {
    return new RolloutPolicyContext(RiskAssessment.low(), new ApprovalState.NotRequired(), reason);
  }

  private RolloutPolicyContext highRiskContext() {
    return new RolloutPolicyContext(
        highRiskAssessment(), new ApprovalState.RequiredButMissing(), null);
  }

  private RiskAssessment highRiskAssessment() {
    return new RiskAssessment(RiskLevel.HIGH, Set.of(RiskReason.LARGE_PRODUCTION_ROLLOUT_INCREASE));
  }

  private void assertInvariant(RolloutPolicyValidationResult result) {
    assertThat(result.allowed()).isEqualTo(result.violations().isEmpty());
  }

  private FeatureFlag flag(int rolloutPercentage, boolean killSwitchActive) {
    return flag(
        FeatureFlagStatus.ENABLED,
        Set.of("production"),
        killSwitchActive,
        Set.of("tenant-a"),
        rolloutPercentage);
  }

  private FeatureFlag flag(Set<String> targetEnvironments, int rolloutPercentage) {
    return flag(
        FeatureFlagStatus.ENABLED,
        targetEnvironments,
        false,
        Set.of("tenant-a"),
        rolloutPercentage);
  }

  private FeatureFlag flag(
      FeatureFlagStatus status, Set<String> targetEnvironments, boolean killSwitchActive) {
    return flag(status, targetEnvironments, killSwitchActive, Set.of("tenant-a"), 25);
  }

  private FeatureFlag flag(Set<String> tenantAllowlist, boolean killSwitchActive) {
    return flag(
        FeatureFlagStatus.ENABLED, Set.of("production"), killSwitchActive, tenantAllowlist, 25);
  }

  private FeatureFlag flag(
      FeatureFlagStatus status,
      Set<String> targetEnvironments,
      boolean killSwitchActive,
      Set<String> tenantAllowlist,
      int rolloutPercentage) {
    return new FeatureFlag(
        "checkout-redesign",
        status,
        targetEnvironments,
        killSwitchActive,
        tenantAllowlist,
        rolloutPercentage);
  }
}
