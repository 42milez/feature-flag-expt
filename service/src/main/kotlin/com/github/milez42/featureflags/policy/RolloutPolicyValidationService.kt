package com.github.milez42.featureflags.policy

import com.github.milez42.featureflags.flags.FeatureFlagService
import com.github.milez42.featureflags.flags.toDomainFlag
import com.github.milez42.featureflags.flags.withProposedChange
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RolloutPolicyValidationService(
    private val featureFlagService: FeatureFlagService,
    private val validator: RolloutPolicyValidator,
    private val riskClassifier: RolloutRiskClassifier,
) {
  @Transactional(readOnly = true)
  fun validate(
      flagKey: String,
      request: RolloutPolicyValidationRequest,
  ): RolloutPolicyValidationResponse {
    val current = featureFlagService.get(flagKey).toDomainFlag()
    val proposed = current.withProposedChange(request.proposedChange)
    val risk = riskClassifier.classify(current, proposed)
    val approval =
        if (risk.requiresApproval()) ApprovalState.RequiredButMissing()
        else ApprovalState.NotRequired()
    val context = RolloutPolicyContext(risk, approval, request.reason)
    val result = validator.validate(current, proposed, context)

    return RolloutPolicyValidationResponse(
        result.flagKey(),
        result.allowed(),
        result.violations(),
    )
  }
}
