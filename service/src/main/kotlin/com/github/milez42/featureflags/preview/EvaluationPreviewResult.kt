package com.github.milez42.featureflags.preview

import com.github.milez42.featureflags.flags.EvaluationReason
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Evaluation result captured inside a preview diff.")
data class EvaluationPreviewResult(
    @field:Schema(description = "Whether the flag is enabled.", example = "true")
    val enabled: Boolean,
    @field:Schema(description = "Reason returned by the evaluator.", example = "ROLLOUT_MATCH")
    val reason: EvaluationReason,
    @field:Schema(description = "Rollout bucket, when bucket evaluation was used.", example = "42")
    val bucket: Int?,
)
