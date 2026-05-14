package com.github.milez42.featureflags.policy

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Result of validating a proposed rollout policy change.")
data class RolloutPolicyValidationResult(
    val flagKey: String,
    @field:Schema(description = "True only when no policy violations were found.")
    val allowed: Boolean,
    val violations: List<RolloutPolicyViolation>,
)
