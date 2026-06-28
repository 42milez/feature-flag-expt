package com.github.milez42.featureflags.policy;

import com.github.milez42.featureflags.flags.Environment;
import com.github.milez42.featureflags.flags.FeatureFlag;
import java.util.EnumSet;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class RolloutRiskClassifier {
  public RiskAssessment classify(FeatureFlag current, FeatureFlag proposed) {
    Objects.requireNonNull(current, "current must not be null");
    Objects.requireNonNull(proposed, "proposed must not be null");

    EnumSet<RiskReason> reasons = EnumSet.noneOf(RiskReason.class);
    if (!ProductionExposure.canServeProductionWithoutAllowlist(current)
        && ProductionExposure.canServeProductionWithoutAllowlist(proposed)) {
      reasons.add(RiskReason.PRODUCTION_EXPOSURE_EXPANDED);
    }
    if (effectiveProductionRollout(proposed) - effectiveProductionRollout(current) >= 50) {
      reasons.add(RiskReason.LARGE_PRODUCTION_ROLLOUT_INCREASE);
    }

    return reasons.isEmpty() ? RiskAssessment.low() : new RiskAssessment(RiskLevel.HIGH, reasons);
  }

  private int effectiveProductionRollout(FeatureFlag flag) {
    return flag.targetEnvironments().contains(Environment.PRODUCTION.value())
        ? flag.rolloutPercentage()
        : 0;
  }
}
