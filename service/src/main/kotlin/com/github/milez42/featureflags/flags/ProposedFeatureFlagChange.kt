package com.github.milez42.featureflags.flags

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Partial feature flag change.")
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
