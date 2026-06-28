package com.github.milez42.featureflags.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.milez42.featureflags.flags.FeatureFlag;
import com.github.milez42.featureflags.flags.FeatureFlagStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RolloutRiskClassifierTest {
  private final RolloutRiskClassifier classifier = new RolloutRiskClassifier();

  @Test
  void productionExposureExpansionIsHighRisk() {
    RiskAssessment risk =
        classifier.classify(
            flag(FeatureFlagStatus.DISABLED, Set.of("production"), false, Set.of(), 25),
            flag(FeatureFlagStatus.ENABLED, Set.of("production"), false, Set.of(), 25));

    assertThat(risk.level()).isEqualTo(RiskLevel.HIGH);
    assertThat(risk.reasons()).containsExactly(RiskReason.PRODUCTION_EXPOSURE_EXPANDED);
    assertThat(risk.requiresApproval()).isTrue();
  }

  @Test
  void largeProductionRolloutIncreaseIsHighRisk() {
    RiskAssessment risk = classifier.classify(productionFlag(20), productionFlag(70));

    assertThat(risk.level()).isEqualTo(RiskLevel.HIGH);
    assertThat(risk.reasons()).containsExactly(RiskReason.LARGE_PRODUCTION_ROLLOUT_INCREASE);
  }

  @Test
  void stagingEightyToProductionEightyIsAPlusEightyProductionRolloutIncrease() {
    RiskAssessment risk =
        classifier.classify(
            flag(FeatureFlagStatus.ENABLED, Set.of("staging"), false, Set.of("tenant-a"), 80),
            flag(FeatureFlagStatus.ENABLED, Set.of("production"), false, Set.of("tenant-a"), 80));

    assertThat(risk.reasons()).containsExactly(RiskReason.LARGE_PRODUCTION_ROLLOUT_INCREASE);
  }

  @Test
  void directZeroToFullProductionUpdateIsALargeProductionRolloutIncrease() {
    RiskAssessment risk = classifier.classify(productionFlag(0), productionFlag(100));

    assertThat(risk.reasons()).containsExactly(RiskReason.LARGE_PRODUCTION_ROLLOUT_INCREASE);
  }

  @Test
  void stagingOnlyUpdateIsLowRisk() {
    RiskAssessment risk =
        classifier.classify(
            flag(FeatureFlagStatus.ENABLED, Set.of("staging"), false, Set.of("tenant-a"), 25),
            flag(FeatureFlagStatus.ENABLED, Set.of("staging"), false, Set.of("tenant-a"), 75));

    assertLowRisk(risk);
  }

  @Test
  void noOpUpdateIsLowRisk() {
    RiskAssessment risk = classifier.classify(productionFlag(25), productionFlag(25));

    assertLowRisk(risk);
  }

  @Test
  void productionExposureReductionIsLowRisk() {
    RiskAssessment risk =
        classifier.classify(
            flag(FeatureFlagStatus.ENABLED, Set.of("production"), false, Set.of(), 25),
            flag(FeatureFlagStatus.DISABLED, Set.of("production"), false, Set.of(), 25));

    assertLowRisk(risk);
  }

  @Test
  void productionRolloutIncreaseBelowFiftyPercentagePointsIsLowRisk() {
    RiskAssessment risk = classifier.classify(productionFlag(10), productionFlag(59));

    assertLowRisk(risk);
  }

  private void assertLowRisk(RiskAssessment risk) {
    assertThat(risk).isEqualTo(RiskAssessment.low());
    assertThat(risk.requiresApproval()).isFalse();
  }

  private FeatureFlag productionFlag(int rolloutPercentage) {
    return flag(
        FeatureFlagStatus.ENABLED,
        Set.of("production"),
        false,
        Set.of("tenant-a"),
        rolloutPercentage);
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
