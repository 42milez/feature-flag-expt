package com.github.milez42.featureflags.policy;

import com.github.milez42.featureflags.flags.Environment;
import com.github.milez42.featureflags.flags.FeatureFlag;
import com.github.milez42.featureflags.flags.FeatureFlagStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class RolloutPolicyValidator {
  public RolloutPolicyValidationResult validate(
      FeatureFlag current, FeatureFlag proposed, RolloutPolicyContext context) {
    Objects.requireNonNull(current, "current must not be null");
    Objects.requireNonNull(proposed, "proposed must not be null");
    Objects.requireNonNull(context, "context must not be null");

    String production = Environment.PRODUCTION.value();
    List<RolloutPolicyViolation> violations = new ArrayList<>();

    // The current implementation catches only direct 0 -> 100 production rollouts.
    // Partial-to-full jumps should be handled by a future step-size policy if needed.
    if (current.rolloutPercentage() == 0
        && proposed.rolloutPercentage() == 100
        && proposed.targetEnvironments().contains(production)) {
      violations.add(fullProductionRollout());
    }

    if (targetsProductionWithoutKillSwitch(proposed, production)) {
      violations.add(productionWithoutKillSwitch());
    }

    if (context.isHighRisk() && !context.isApprovalGranted()) {
      violations.add(highRiskRequiresApproval());
    }

    if (enablesProductionWithoutAllowlist(proposed, production) && !context.hasReason()) {
      violations.add(productionEnablementRequiresReason());
    }

    return RolloutPolicyValidationResult.from(current.flagKey(), violations);
  }

  private boolean targetsProductionWithoutKillSwitch(FeatureFlag flag, String production) {
    return flag.targetEnvironments().contains(production) && !flag.killSwitchActive();
  }

  private boolean enablesProductionWithoutAllowlist(FeatureFlag flag, String production) {
    return flag.status() == FeatureFlagStatus.ENABLED
        && flag.targetEnvironments().contains(production)
        && !flag.killSwitchActive()
        && flag.tenantAllowlist().isEmpty();
  }

  private RolloutPolicyViolation fullProductionRollout() {
    return new RolloutPolicyViolation(
        "FULL_PRODUCTION_ROLLOUT",
        "Rolling out from 0% to 100% in production in a single step is not allowed.",
        Severity.ERROR);
  }

  private RolloutPolicyViolation productionWithoutKillSwitch() {
    return new RolloutPolicyViolation(
        "PRODUCTION_WITHOUT_KILL_SWITCH",
        "Production-targeted changes without an active kill switch are not allowed.",
        Severity.ERROR);
  }

  private RolloutPolicyViolation highRiskRequiresApproval() {
    return new RolloutPolicyViolation(
        "HIGH_RISK_REQUIRES_APPROVAL",
        "High-risk changes require approval before rollout.",
        Severity.ERROR);
  }

  private RolloutPolicyViolation productionEnablementRequiresReason() {
    return new RolloutPolicyViolation(
        "PRODUCTION_ENABLEMENT_REQUIRES_REASON",
        "Enabling production access without a tenant allowlist requires a reason.",
        Severity.ERROR);
  }
}
