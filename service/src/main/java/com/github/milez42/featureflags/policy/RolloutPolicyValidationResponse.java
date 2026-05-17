package com.github.milez42.featureflags.policy;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

@Schema(description = "Response for validating a proposed rollout policy change.")
public record RolloutPolicyValidationResponse(
    @Schema(description = "Feature flag key.", example = "checkout-redesign") String flagKey,
    @Schema(description = "True only when no policy violations were found.") boolean allowed,
    @Schema(description = "Policy violations found for the proposed change.")
        List<RolloutPolicyViolation> violations) {
  public RolloutPolicyValidationResponse {
    Objects.requireNonNull(flagKey, "flagKey must not be null");
    Objects.requireNonNull(violations, "violations must not be null");
    violations = List.copyOf(violations);
  }
}
