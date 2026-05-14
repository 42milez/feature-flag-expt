package com.github.milez42.featureflags.policy

import com.github.milez42.featureflags.flags.ProposedFeatureFlagChange
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(description = "Request to validate a proposed feature flag change.")
data class RolloutPolicyValidationRequest(
    @field:Schema(description = "Partial change to validate.")
    @field:NotNull
    @field:Valid
    val proposedChange: ProposedFeatureFlagChange,
    @field:Schema(
        description =
            "Whether this change is considered operationally high risk. Null is treated as false."
    )
    val highRisk: Boolean? = false,
    @field:Schema(
        description =
            "Whether the required approval for a high-risk change was granted. Null is treated as false."
    )
    val approvalGranted: Boolean? = false,
    @field:Schema(
        description = "Business reason for enabling production access without an allowlist."
    )
    @field:Size(max = 1000)
    val reason: String? = null,
)
