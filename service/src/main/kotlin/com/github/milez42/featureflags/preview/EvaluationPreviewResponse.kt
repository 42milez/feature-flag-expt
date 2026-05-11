package com.github.milez42.featureflags.preview

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Preview result for a proposed feature flag change.")
data class EvaluationPreviewResponse(
    @field:Schema(description = "Feature flag key.", example = "checkout-redesign")
    val flagKey: String,
    @field:Schema(description = "Per-sample evaluation diffs.") val diffs: List<EvaluationDiff>,
    @field:Schema(description = "Aggregate counts for the preview.")
    val summary: EvaluationPreviewSummary,
)
