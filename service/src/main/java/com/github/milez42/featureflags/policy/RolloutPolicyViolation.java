package com.github.milez42.featureflags.policy;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@Schema(description = "Rollout policy violation.")
public record RolloutPolicyViolation(String code, String message, Severity severity) {
  public RolloutPolicyViolation {
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(message, "message must not be null");
    Objects.requireNonNull(severity, "severity must not be null");
  }
}
