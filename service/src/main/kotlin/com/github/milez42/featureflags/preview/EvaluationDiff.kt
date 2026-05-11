package com.github.milez42.featureflags.preview

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Before and after evaluation result for one sample context.")
data class EvaluationDiff(
    @field:Schema(description = "Sample environment.", example = "production")
    val environment: String,
    @field:Schema(description = "Sample tenant ID.", example = "tenant-a") val tenantId: String?,
    @field:Schema(description = "Sample user ID.", example = "user-a") val userId: String?,
    @field:Schema(description = "Evaluation result for the current persisted flag.")
    val before: EvaluationPreviewResult,
    @field:Schema(description = "Evaluation result for the proposed in-memory flag.")
    val after: EvaluationPreviewResult,
    @field:Schema(description = "Whether the enabled value changed.", example = "true")
    val changed: Boolean,
)
