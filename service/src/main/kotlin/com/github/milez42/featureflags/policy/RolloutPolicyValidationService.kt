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
) {
  @Transactional(readOnly = true)
  fun validate(
      flagKey: String,
      request: RolloutPolicyValidationRequest,
  ): RolloutPolicyValidationResponse {
    val current = featureFlagService.get(flagKey).toDomainFlag()
    val proposed = current.withProposedChange(request.proposedChange)
    val context = RolloutPolicyContext(request.highRisk, request.approvalGranted, request.reason)
    val result = validator.validate(current, proposed, context)

    return RolloutPolicyValidationResponse(
        flagKey = result.flagKey(),
        allowed = result.allowed(),
        violations = result.violations(),
    )
  }
}
