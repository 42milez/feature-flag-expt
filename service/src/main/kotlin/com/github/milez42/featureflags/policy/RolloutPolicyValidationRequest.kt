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
    // TODO: Derive high-risk classification on the server instead of trusting this
    // client-supplied flag. A caller-controlled risk flag can suppress approval requirements by
    // sending false, and it does not prove that the risk assessment considered the flag's business
    // criticality, production exposure, tenant impact, rollout size, actor permissions, release
    // freezes, incident state, or change-request risk classification once those concepts exist.
    val highRisk: Boolean? = false,
    @field:Schema(
        description =
            "Whether the required approval for a high-risk change was granted. Null is treated as false."
    )
    // TODO: Replace this client-supplied flag with a server-verified approval reference once an
    // approval workflow exists. A caller-controlled boolean can bypass the high-risk approval
    // policy and does not prove who approved the change, whether the approval belongs to this exact
    // proposal, or whether the approval is still valid. Prefer a changeRequestId or approvalId that
    // the server can verify against approval state, actor permissions, expiry, and audit history.
    val approvalGranted: Boolean? = false,
    @field:Schema(
        description = "Business reason for enabling production access without an allowlist."
    )
    @field:Size(max = 1000)
    val reason: String? = null,
)
