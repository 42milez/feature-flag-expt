package com.github.milez42.featureflags.preview

import com.github.milez42.featureflags.flags.EvaluationContext
import com.github.milez42.featureflags.flags.EvaluationResult
import com.github.milez42.featureflags.flags.FeatureFlag
import com.github.milez42.featureflags.flags.FeatureFlagEvaluator
import com.github.milez42.featureflags.flags.FeatureFlagResponse
import com.github.milez42.featureflags.flags.FeatureFlagService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EvaluationPreviewService(
    private val featureFlagService: FeatureFlagService,
    private val evaluator: FeatureFlagEvaluator,
) {
  @Transactional(readOnly = true)
  fun preview(flagKey: String, request: EvaluationPreviewRequest): EvaluationPreviewResponse {
    val before = featureFlagService.get(flagKey).toDomain()
    val after = before.withProposedChange(request.proposedChange)

    // Preview is intentionally sample-based: the persisted flag supplies the baseline rules,
    // while the request supplies proposed overrides and the contexts to evaluate.
    // Reusing each sample context for before and after isolates the effect of the flag change.
    val diffs =
        request.sampleContexts.map { sample ->
          val context = EvaluationContext(sample.environment, sample.tenantId, sample.userId)
          val beforeResult = evaluator.evaluate(before, context)
          val afterResult = evaluator.evaluate(after, context)

          EvaluationDiff(
              environment = sample.environment,
              tenantId = sample.tenantId,
              userId = sample.userId,
              before = beforeResult.toPreviewResult(),
              after = afterResult.toPreviewResult(),
              changed = beforeResult.enabled() != afterResult.enabled(),
          )
        }

    return EvaluationPreviewResponse(
        flagKey = before.flagKey(),
        diffs = diffs,
        summary = diffs.summary(),
    )
  }

  private fun FeatureFlagResponse.toDomain(): FeatureFlag =
      FeatureFlag(
          flagKey(),
          status(),
          targetEnvironments(),
          killSwitchActive(),
          tenantAllowlist(),
          rolloutPercentage(),
      )

  private fun FeatureFlag.withProposedChange(change: ProposedFeatureFlagChange): FeatureFlag =
      FeatureFlag(
          flagKey(),
          change.status ?: status(),
          change.targetEnvironments?.map { it.value() }?.toSet() ?: targetEnvironments(),
          change.killSwitchActive ?: killSwitchActive(),
          change.tenantAllowlist ?: tenantAllowlist(),
          change.rolloutPercentage ?: rolloutPercentage(),
      )

  private fun EvaluationResult.toPreviewResult(): EvaluationPreviewResult =
      EvaluationPreviewResult(enabled = enabled(), reason = reason(), bucket = bucket())

  private fun List<EvaluationDiff>.summary(): EvaluationPreviewSummary =
      EvaluationPreviewSummary(
          sampleCount = size,
          beforeEnabledCount = count { it.before.enabled },
          beforeDisabledCount = count { !it.before.enabled },
          enabledCount = count { it.after.enabled },
          disabledCount = count { !it.after.enabled },
          changedCount = count { it.changed },
      )
}
