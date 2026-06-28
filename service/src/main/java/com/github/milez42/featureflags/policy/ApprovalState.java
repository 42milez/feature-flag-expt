package com.github.milez42.featureflags.policy;

import java.util.Objects;
import java.util.UUID;

public sealed interface ApprovalState
    permits ApprovalState.NotRequired, ApprovalState.RequiredButMissing, ApprovalState.Verified {
  record NotRequired() implements ApprovalState {}

  record RequiredButMissing() implements ApprovalState {}

  record Verified(UUID approvalId, String approver) implements ApprovalState {
    public Verified {
      Objects.requireNonNull(approvalId, "approvalId must not be null");
      Objects.requireNonNull(approver, "approver must not be null");
      if (approver.isBlank()) {
        throw new IllegalArgumentException("approver must not be blank");
      }
    }
  }
}
