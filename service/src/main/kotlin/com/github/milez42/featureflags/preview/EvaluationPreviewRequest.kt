package com.github.milez42.featureflags.preview

import com.github.milez42.featureflags.flags.ProposedFeatureFlagChange
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(description = "Request to preview a proposed feature flag change.")
data class EvaluationPreviewRequest(
    @field:Schema(description = "Partial change to apply in memory.")
    @field:NotNull
    @field:Valid
    val proposedChange: ProposedFeatureFlagChange,
    @field:Schema(description = "Sample contexts to evaluate before and after the change.")
    @field:NotEmpty
    @field:Size(max = 100)
    @field:Valid
    val sampleContexts: List<EvaluationPreviewContext>,
)

@Schema(description = "Sample context to evaluate during preview.")
data class EvaluationPreviewContext(
    @field:Schema(description = "Environment to evaluate.", example = "production", minLength = 1)
    @field:NotBlank
    @field:Size(max = 255)
    val environment: String,
    @field:Schema(
        description = "Optional tenant ID to evaluate. Omit or send null when unknown.",
        example = "tenant-a",
        pattern = ".*\\S.*",
    )
    @field:Size(max = 255)
    @field:Pattern(regexp = ".*\\S.*")
    val tenantId: String?,
    @field:Schema(
        description = "Optional user ID to evaluate. Omit or send null when unknown.",
        example = "user-a",
        pattern = ".*\\S.*",
    )
    @field:Size(max = 255)
    @field:Pattern(regexp = ".*\\S.*")
    val userId: String?,
)
