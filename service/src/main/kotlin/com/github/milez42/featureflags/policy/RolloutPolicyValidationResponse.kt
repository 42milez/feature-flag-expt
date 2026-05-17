package com.github.milez42.featureflags.policy

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Response for validating a proposed rollout policy change.")
data class RolloutPolicyValidationResponse(
    @field:Schema(description = "Feature flag key.", example = "checkout-redesign")
    val flagKey: String,
    @field:Schema(description = "True only when no policy violations were found.")
    val allowed: Boolean,
    @field:Schema(description = "Policy violations found for the proposed change.")
    val violations: List<RolloutPolicyViolation>,
)
