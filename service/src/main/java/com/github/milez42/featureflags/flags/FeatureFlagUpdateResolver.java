package com.github.milez42.featureflags.flags;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlagUpdateResolver {
  public FeatureFlag resolve(FeatureFlag current, FeatureFlagUpdateProposal request) {
    return new FeatureFlag(
        current.flagKey(),
        request.status() == null ? current.status() : request.status(),
        request.targetEnvironments() == null
            ? current.targetEnvironments()
            : targetEnvironmentValues(request.targetEnvironments()),
        request.killSwitchActive() == null
            ? current.killSwitchActive()
            : request.killSwitchActive(),
        request.tenantAllowlist() == null
            ? current.tenantAllowlist()
            : normalizeSet(request.tenantAllowlist()),
        request.rolloutPercentage() == null
            ? current.rolloutPercentage()
            : request.rolloutPercentage());
  }

  public FeatureFlag normalizeSnapshot(FeatureFlag flag) {
    return new FeatureFlag(
        flag.flagKey(),
        flag.status(),
        normalizeSet(flag.targetEnvironments()),
        flag.killSwitchActive(),
        normalizeSet(flag.tenantAllowlist()),
        flag.rolloutPercentage());
  }

  public Set<TargetEnvironmentEntity> targetEnvironments(Set<Environment> values) {
    if (values == null) {
      return Set.of();
    }
    return values.stream()
        .map(env -> new TargetEnvironmentEntity(env.value()))
        .sorted(Comparator.comparing(TargetEnvironmentEntity::environment))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public Set<TenantAllowlistEntity> tenantAllowlist(Set<String> values) {
    return normalizeSet(values).stream()
        .map(TenantAllowlistEntity::new)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private Set<String> targetEnvironmentValues(Set<Environment> values) {
    return values.stream()
        .map(Environment::value)
        .sorted()
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private Set<String> normalizeSet(Set<String> values) {
    if (values == null) {
      return Set.of();
    }
    return values.stream()
        .map(value -> normalizeRequired(value, "collection value"))
        .sorted()
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private String normalizeRequired(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    String normalized = value.trim();
    if (normalized.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return normalized;
  }
}
