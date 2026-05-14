package com.github.milez42.featureflags.policy

import com.github.milez42.featureflags.flags.Environment
import com.github.milez42.featureflags.flags.FeatureFlag
import com.github.milez42.featureflags.flags.FeatureFlagService
import com.github.milez42.featureflags.flags.FeatureFlagStatus
import com.github.milez42.featureflags.flags.toDomainFlag
import com.github.milez42.featureflags.flags.withProposedChange
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RolloutPolicyValidator(private val featureFlagService: FeatureFlagService) {
  @Transactional(readOnly = true)
  fun validate(
      flagKey: String,
      request: RolloutPolicyValidationRequest,
  ): RolloutPolicyValidationResult {
    val current = featureFlagService.get(flagKey).toDomainFlag()
    val proposed = current.withProposedChange(request.proposedChange)
    val production = Environment.PRODUCTION.value()

    val violations = buildList {
      // Phase 6 catches only direct 0 -> 100 production rollouts. Partial-to-full jumps should be
      // handled by a future step-size policy if that behavior becomes required.
      if (
          current.rolloutPercentage() == 0 &&
              proposed.rolloutPercentage() == 100 &&
              proposed.targetEnvironments().contains(production)
      ) {
        add(fullProductionRollout())
      }

      if (proposed.targetsProductionWithoutKillSwitch(production)) {
        add(productionWithoutKillSwitch())
      }

      if (request.highRisk == true && request.approvalGranted != true) {
        add(highRiskRequiresApproval())
      }

      if (
          proposed.enablesProductionWithoutAllowlist(production) && request.reason.isNullOrBlank()
      ) {
        add(productionEnablementRequiresReason())
      }
    }

    return result(current.flagKey(), violations)
  }

  private fun FeatureFlag.targetsProductionWithoutKillSwitch(production: String): Boolean =
      targetEnvironments().contains(production) && !killSwitchActive()

  private fun FeatureFlag.enablesProductionWithoutAllowlist(production: String): Boolean =
      status() == FeatureFlagStatus.ENABLED &&
          targetEnvironments().contains(production) &&
          !killSwitchActive() &&
          tenantAllowlist().isEmpty()
}

private fun result(flagKey: String, violations: List<RolloutPolicyViolation>) =
    RolloutPolicyValidationResult(
        flagKey = flagKey,
        allowed = violations.isEmpty(),
        violations = violations,
    )

private fun fullProductionRollout() =
    RolloutPolicyViolation(
        code = "FULL_PRODUCTION_ROLLOUT",
        message = "Rolling out from 0% to 100% in production in a single step is not allowed.",
        severity = Severity.ERROR,
    )

private fun productionWithoutKillSwitch() =
    RolloutPolicyViolation(
        code = "PRODUCTION_WITHOUT_KILL_SWITCH",
        message = "Production-targeted changes without an active kill switch are not allowed.",
        severity = Severity.ERROR,
    )

private fun highRiskRequiresApproval() =
    RolloutPolicyViolation(
        code = "HIGH_RISK_REQUIRES_APPROVAL",
        message = "High-risk changes require approval before rollout.",
        severity = Severity.ERROR,
    )

private fun productionEnablementRequiresReason() =
    RolloutPolicyViolation(
        code = "PRODUCTION_ENABLEMENT_REQUIRES_REASON",
        message = "Enabling production access without a tenant allowlist requires a reason.",
        severity = Severity.ERROR,
    )
