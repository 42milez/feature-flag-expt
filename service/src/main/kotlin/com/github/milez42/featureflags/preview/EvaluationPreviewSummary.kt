package com.github.milez42.featureflags.preview

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Aggregate counts for a preview response.")
data class EvaluationPreviewSummary(
    @field:Schema(description = "Number of sample contexts evaluated.", example = "3")
    val sampleCount: Int,
    @field:Schema(description = "Samples enabled before the proposed change.", example = "1")
    val beforeEnabledCount: Int,
    @field:Schema(
        description = "Convenience count of samples disabled before the proposed change.",
        example = "2",
    )
    val beforeDisabledCount: Int,
    @field:Schema(description = "Samples enabled after the proposed change.", example = "2")
    val enabledCount: Int,
    @field:Schema(
        description = "Convenience count of samples disabled after the proposed change.",
        example = "1",
    )
    val disabledCount: Int,
    @field:Schema(description = "Samples whose enabled value changed.", example = "1")
    val changedCount: Int,
)
