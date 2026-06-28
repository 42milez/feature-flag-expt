package com.github.milez42.featureflags.policy;

import java.util.Objects;

public record RolloutPolicyContext(RiskAssessment risk, ApprovalState approval, String reason) {
  public RolloutPolicyContext {
    Objects.requireNonNull(risk, "risk must not be null");
    Objects.requireNonNull(approval, "approval must not be null");
  }

  public boolean hasReason() {
    return reason != null && !reason.isBlank();
  }
}
