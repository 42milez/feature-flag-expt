package com.github.milez42.featureflags.approval;

import com.github.milez42.featureflags.policy.RiskReason;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record RiskReasonSet(Set<RiskReason> values) {
  public RiskReasonSet {
    Objects.requireNonNull(values, "values must not be null");
    values =
        values.stream()
            .sorted(Comparator.comparing(RiskReason::name))
            .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
