package com.github.milez42.featureflags.preview

import com.github.milez42.featureflags.flags.Environment
import com.github.milez42.featureflags.flags.FeatureFlagStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
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

@Schema(description = "Partial feature flag change used only for preview.")
data class ProposedFeatureFlagChange(
    @field:Schema(description = "Proposed flag status.", example = "ENABLED")
    val status: FeatureFlagStatus? = null,
    @field:Schema(
        description = "Proposed target environments. Empty input clears target environments.",
        example = "[\"production\", \"staging\"]",
    )
    val targetEnvironments: Set<Environment>? = null,
    @field:Schema(description = "Proposed kill switch state.", example = "false")
    val killSwitchActive: Boolean? = null,
    @field:Schema(
        description = "Proposed tenant allowlist. Empty input clears the allowlist.",
        example = "[\"tenant-a\"]",
    )
    @field:Size(max = 1000)
    val tenantAllowlist: Set<@NotBlank @Size(max = 255) String>? = null,
    @field:Schema(description = "Proposed percentage rollout from 0 to 100.", example = "50")
    @field:Min(0)
    @field:Max(100)
    val rolloutPercentage: Int? = null,
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
