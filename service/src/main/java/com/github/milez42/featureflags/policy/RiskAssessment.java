package com.github.milez42.featureflags.policy;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record RiskAssessment(RiskLevel level, Set<RiskReason> reasons) {
  public RiskAssessment {
    Objects.requireNonNull(level, "level must not be null");
    Objects.requireNonNull(reasons, "reasons must not be null");

    if (level == RiskLevel.LOW && !reasons.isEmpty()) {
      throw new IllegalArgumentException("low risk must not include reasons");
    }
    if (level == RiskLevel.HIGH && reasons.isEmpty()) {
      throw new IllegalArgumentException("high risk must include at least one reason");
    }

    // Use EnumSet instead of Set.copyOf to keep the compact enum-specific storage, then wrap the
    // defensive copy so callers cannot mutate stored risk reasons.
    reasons = reasons.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(reasons));
  }

  public boolean requiresApproval() {
    return level == RiskLevel.HIGH;
  }

  public static RiskAssessment low() {
    return new RiskAssessment(RiskLevel.LOW, Set.of());
  }
}
