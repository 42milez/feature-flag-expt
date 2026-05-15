package com.github.milez42.featureflags.policy;

public record RolloutPolicyContext(Boolean highRisk, Boolean approvalGranted, String reason) {
  public boolean isHighRisk() {
    return Boolean.TRUE.equals(highRisk);
  }

  public boolean isApprovalGranted() {
    return Boolean.TRUE.equals(approvalGranted);
  }

  public boolean hasReason() {
    return reason != null && !reason.isBlank();
  }
}
