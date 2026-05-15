package com.github.milez42.featureflags.policy;

import java.util.Objects;

public class RolloutPolicyViolationException extends RuntimeException {
  private final RolloutPolicyValidationResult result;

  public RolloutPolicyViolationException(RolloutPolicyValidationResult result) {
    super("Rollout policy validation failed");
    this.result = Objects.requireNonNull(result, "result must not be null");
  }

  public RolloutPolicyValidationResult result() {
    return result;
  }
}
