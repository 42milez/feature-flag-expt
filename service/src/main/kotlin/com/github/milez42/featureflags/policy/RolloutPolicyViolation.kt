package com.github.milez42.featureflags.policy

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class Severity {
  ERROR
}

@Schema(description = "Rollout policy violation.")
data class RolloutPolicyViolation(
    val code: String,
    val message: String,
    val severity: Severity,
)
