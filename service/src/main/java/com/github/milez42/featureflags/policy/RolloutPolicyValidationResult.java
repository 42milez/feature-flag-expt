package com.github.milez42.featureflags.policy;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

@Schema(description = "Result of validating a proposed rollout policy change.")
public record RolloutPolicyValidationResult(
    String flagKey,
    @Schema(description = "True only when no policy violations were found.") boolean allowed,
    List<RolloutPolicyViolation> violations) {
  public RolloutPolicyValidationResult {
    Objects.requireNonNull(flagKey, "flagKey must not be null");
    Objects.requireNonNull(violations, "violations must not be null");
    violations = List.copyOf(violations);
  }

  public static RolloutPolicyValidationResult from(
      String flagKey, List<RolloutPolicyViolation> violations) {
    return new RolloutPolicyValidationResult(flagKey, violations.isEmpty(), violations);
  }
}
