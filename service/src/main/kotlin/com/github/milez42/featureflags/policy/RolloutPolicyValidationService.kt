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
  ): RolloutPolicyValidationResult {
    val current = featureFlagService.get(flagKey).toDomainFlag()
    val proposed = current.withProposedChange(request.proposedChange)
    val context = RolloutPolicyContext(request.highRisk, request.approvalGranted, request.reason)

    return validator.validate(current, proposed, context)
  }
}
