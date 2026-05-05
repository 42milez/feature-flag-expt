package com.github.milez42.featureflags.flags;

import com.github.milez42.featureflags.audit.AuditEventDetails;
import com.github.milez42.featureflags.audit.AuditEventService;
import com.github.milez42.featureflags.audit.AuditEventType;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureFlagService {
  private final FeatureFlagRepository repository;
  private final FeatureFlagEvaluator evaluator;
  private final AuditEventService auditEventService;

  public FeatureFlagService(
      FeatureFlagRepository repository,
      FeatureFlagEvaluator evaluator,
      AuditEventService auditEventService) {
    this.repository = repository;
    this.evaluator = evaluator;
    this.auditEventService = auditEventService;
  }

  @Transactional
  public FeatureFlagResponse create(CreateFeatureFlagRequest request) {
    String flagKey = normalizeRequired(request.flagKey(), "flagKey");
    if (repository.existsById(flagKey)) {
      throw new FeatureFlagDuplicateException(flagKey);
    }

    FeatureFlagEntity entity =
        FeatureFlagEntity.create(
            flagKey,
            request.status(),
            targetEnvironments(request.targetEnvironments()),
            request.killSwitchActive(),
            tenantAllowlist(request.tenantAllowlist()),
            request.rolloutPercentage());

    FeatureFlagEntity saved = repository.save(entity);
    auditEventService.record(
        saved.flagKey(),
        AuditEventType.FLAG_CREATED,
        new AuditEventDetails.FlagCreatedDetails(
            saved.status(),
            saved.rolloutPercentage(),
            saved.killSwitchActive(),
            targetEnvironmentValues(saved),
            tenantAllowlistValues(saved)));

    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public FeatureFlagResponse get(String flagKey) {
    return toResponse(findEntity(flagKey));
  }

  @Transactional
  public FeatureFlagResponse update(String flagKey, UpdateFeatureFlagRequest request) {
    FeatureFlagEntity existing = findEntity(flagKey);
    FeatureFlagEntity updated =
        new FeatureFlagEntity(
            existing.flagKey(),
            request.status() == null ? existing.status() : request.status(),
            (request.targetEnvironments() == null || request.targetEnvironments().isEmpty())
                ? existing.targetEnvironments()
                : targetEnvironments(request.targetEnvironments()),
            request.killSwitchActive() == null
                ? existing.killSwitchActive()
                : request.killSwitchActive(),
            request.tenantAllowlist() == null
                ? existing.tenantAllowlist()
                : tenantAllowlist(request.tenantAllowlist()),
            request.rolloutPercentage() == null
                ? existing.rolloutPercentage()
                : request.rolloutPercentage());

    FeatureFlagEntity saved = repository.save(updated);
    recordUpdateEvents(existing, saved);

    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public EvaluateFeatureFlagResponse evaluate(EvaluateFeatureFlagRequest request) {
    FeatureFlagEntity entity = findEntity(request.flagKey());
    EvaluationResult result =
        evaluator.evaluate(
            toDomain(entity),
            new EvaluationContext(
                normalizeRequired(request.environment(), "environment"),
                normalizeOptional(request.tenantId()),
                normalizeOptional(request.userId())));

    return new EvaluateFeatureFlagResponse(
        entity.flagKey(), result.enabled(), result.reason(), result.bucket());
  }

  private FeatureFlagEntity findEntity(String flagKey) {
    String normalizedFlagKey = normalizeRequired(flagKey, "flagKey");
    return repository
        .findById(normalizedFlagKey)
        .orElseThrow(() -> new FeatureFlagNotFoundException(normalizedFlagKey));
  }

  private FeatureFlag toDomain(FeatureFlagEntity entity) {
    return new FeatureFlag(
        entity.flagKey(),
        entity.status(),
        entity.targetEnvironments().stream()
            .map(TargetEnvironmentEntity::environment)
            .collect(Collectors.toCollection(LinkedHashSet::new)),
        entity.killSwitchActive(),
        entity.tenantAllowlist().stream()
            .map(TenantAllowlistEntity::tenantId)
            .collect(Collectors.toCollection(LinkedHashSet::new)),
        entity.rolloutPercentage());
  }

  private FeatureFlagResponse toResponse(FeatureFlagEntity entity) {
    return new FeatureFlagResponse(
        entity.flagKey(),
        entity.status(),
        entity.targetEnvironments().stream()
            .map(TargetEnvironmentEntity::environment)
            .collect(Collectors.toCollection(LinkedHashSet::new)),
        entity.killSwitchActive(),
        entity.tenantAllowlist().stream()
            .map(TenantAllowlistEntity::tenantId)
            .collect(Collectors.toCollection(LinkedHashSet::new)),
        entity.rolloutPercentage());
  }

  private void recordUpdateEvents(FeatureFlagEntity existing, FeatureFlagEntity updated) {
    if (existing.status() != updated.status()) {
      if (updated.status() == FeatureFlagStatus.ENABLED) {
        auditEventService.record(
            updated.flagKey(),
            AuditEventType.FLAG_ENABLED,
            new AuditEventDetails.FlagEnabledDetails(
                "status", existing.status(), updated.status()));
      } else {
        auditEventService.record(
            updated.flagKey(),
            AuditEventType.FLAG_DISABLED,
            new AuditEventDetails.FlagDisabledDetails(
                "status", existing.status(), updated.status()));
      }
    }

    if (existing.rolloutPercentage() != updated.rolloutPercentage()) {
      auditEventService.record(
          updated.flagKey(),
          AuditEventType.ROLLOUT_PERCENTAGE_CHANGED,
          new AuditEventDetails.RolloutPercentageChangedDetails(
              "rolloutPercentage", existing.rolloutPercentage(), updated.rolloutPercentage()));
    }

    Set<String> oldAllowlist = tenantAllowlistValues(existing);
    Set<String> newAllowlist = tenantAllowlistValues(updated);
    if (!oldAllowlist.equals(newAllowlist)) {
      auditEventService.record(
          updated.flagKey(),
          AuditEventType.TENANT_ALLOWLIST_CHANGED,
          new AuditEventDetails.TenantAllowlistChangedDetails(
              "tenantAllowlist", oldAllowlist, newAllowlist));
    }

    if (existing.killSwitchActive() != updated.killSwitchActive()) {
      if (updated.killSwitchActive()) {
        auditEventService.record(
            updated.flagKey(),
            AuditEventType.KILL_SWITCH_ENABLED,
            new AuditEventDetails.KillSwitchEnabledDetails(
                "killSwitchActive", existing.killSwitchActive(), updated.killSwitchActive()));
      } else {
        auditEventService.record(
            updated.flagKey(),
            AuditEventType.KILL_SWITCH_DISABLED,
            new AuditEventDetails.KillSwitchDisabledDetails(
                "killSwitchActive", existing.killSwitchActive(), updated.killSwitchActive()));
      }
    }
  }

  private Set<String> targetEnvironmentValues(FeatureFlagEntity entity) {
    return entity.targetEnvironments().stream()
        .map(TargetEnvironmentEntity::environment)
        .sorted()
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private Set<String> tenantAllowlistValues(FeatureFlagEntity entity) {
    return entity.tenantAllowlist().stream()
        .map(TenantAllowlistEntity::tenantId)
        .sorted()
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private Set<TargetEnvironmentEntity> targetEnvironments(Set<Environment> values) {
    if (values == null) {
      return Set.of();
    }
    return values.stream()
        .map(env -> new TargetEnvironmentEntity(env.value()))
        .sorted(Comparator.comparing(TargetEnvironmentEntity::environment))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private Set<TenantAllowlistEntity> tenantAllowlist(Set<String> values) {
    return normalizeSet(values).stream()
        .map(TenantAllowlistEntity::new)
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

  private String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }
}
