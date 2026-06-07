package com.github.milez42.featureflags.flags;

import com.github.milez42.featureflags.audit.AuditEventDetails;
import com.github.milez42.featureflags.audit.AuditEventResponse;
import com.github.milez42.featureflags.audit.AuditEventService;
import com.github.milez42.featureflags.audit.AuditEventType;
import com.github.milez42.featureflags.observability.FeatureFlagMetrics;
import com.github.milez42.featureflags.policy.RolloutPolicyContext;
import com.github.milez42.featureflags.policy.RolloutPolicyValidationResult;
import com.github.milez42.featureflags.policy.RolloutPolicyValidator;
import com.github.milez42.featureflags.policy.RolloutPolicyViolationException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureFlagService {
  private static final Logger LOGGER = LoggerFactory.getLogger(FeatureFlagService.class);

  private final FeatureFlagRepository repository;
  private final FeatureFlagEvaluator evaluator;
  private final RolloutPolicyValidator rolloutPolicyValidator;
  private final AuditEventService auditEventService;
  private final FeatureFlagMetrics metrics;

  public FeatureFlagService(
      FeatureFlagRepository repository,
      FeatureFlagEvaluator evaluator,
      RolloutPolicyValidator rolloutPolicyValidator,
      AuditEventService auditEventService,
      FeatureFlagMetrics metrics) {
    this.repository = repository;
    this.evaluator = evaluator;
    this.rolloutPolicyValidator = rolloutPolicyValidator;
    this.auditEventService = auditEventService;
    this.metrics = metrics;
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

  @Transactional(readOnly = true)
  public List<AuditEventResponse> auditEvents(String flagKey) {
    FeatureFlagEntity entity = findEntity(flagKey);
    return auditEventService.findByFlagKey(entity.flagKey()).stream()
        .map(
            event ->
                new AuditEventResponse(
                    event.id(),
                    event.flagKey(),
                    event.eventType(),
                    event.actor(),
                    event.details(),
                    event.occurredAt()))
        .toList();
  }

  @Transactional
  public FeatureFlagResponse update(String flagKey, UpdateFeatureFlagRequest request) {
    FeatureFlagEntity existing = findEntity(flagKey);
    FeatureFlagEntity updated =
        new FeatureFlagEntity(
            existing.flagKey(),
            request.status() == null ? existing.status() : request.status(),
            request.targetEnvironments() == null
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

    RolloutPolicyValidationResult policyResult =
        rolloutPolicyValidator.validate(
            toDomain(existing),
            toDomain(updated),
            new RolloutPolicyContext(
                request.highRisk(), request.approvalGranted(), request.reason()));
    if (!policyResult.allowed()) {
      throw new RolloutPolicyViolationException(policyResult);
    }

    FeatureFlagEntity saved = repository.save(updated);
    recordUpdateEvents(existing, saved);
    metrics.recordUpdate(saved.flagKey());
    logUpdate(existing, saved);
    if (!existing.killSwitchActive() && saved.killSwitchActive()) {
      metrics.recordKillSwitchEnabled(saved.flagKey());
      logKillSwitchEnabled(saved);
    }

    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public EvaluateFeatureFlagResponse evaluate(EvaluateFeatureFlagRequest request) {
    FeatureFlagEntity entity = findEntity(request.flagKey());
    String environment = normalizeRequired(request.environment(), "environment");
    String tenantId = normalizeOptional(request.tenantId());
    String userId = normalizeOptional(request.userId());
    EvaluationResult result =
        evaluator.evaluate(toDomain(entity), new EvaluationContext(environment, tenantId, userId));
    metrics.recordEvaluation(entity.flagKey(), environment, result.enabled(), result.reason());
    logEvaluation(entity.flagKey(), environment, tenantId, result);

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

    Set<String> oldTargetEnvironments = targetEnvironmentValues(existing);
    Set<String> newTargetEnvironments = targetEnvironmentValues(updated);
    if (!oldTargetEnvironments.equals(newTargetEnvironments)) {
      auditEventService.record(
          updated.flagKey(),
          AuditEventType.TARGET_ENVIRONMENTS_CHANGED,
          new AuditEventDetails.TargetEnvironmentsChangedDetails(
              "targetEnvironments", oldTargetEnvironments, newTargetEnvironments));
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

  private void logEvaluation(
      String flagKey, String environment, String tenantId, EvaluationResult result) {
    LOGGER
        .atInfo()
        .addKeyValue("event", "feature_flag_evaluated")
        .addKeyValue("flagKey", flagKey)
        .addKeyValue("environment", environment)
        .addKeyValue("tenantId", tenantId)
        .addKeyValue("enabled", result.enabled())
        .addKeyValue("reason", result.reason())
        .addKeyValue("bucket", result.bucket())
        .log("feature flag evaluated");
  }

  private void logUpdate(FeatureFlagEntity existing, FeatureFlagEntity updated) {
    LOGGER
        .atInfo()
        .addKeyValue("event", "feature_flag_updated")
        .addKeyValue("flagKey", updated.flagKey())
        .addKeyValue("changedFields", changedFields(existing, updated))
        .log("feature flag updated");
  }

  private void logKillSwitchEnabled(FeatureFlagEntity updated) {
    LOGGER
        .atInfo()
        .addKeyValue("event", "feature_flag_kill_switch_enabled")
        .addKeyValue("flagKey", updated.flagKey())
        .log("feature flag kill switch enabled");
  }

  private List<String> changedFields(FeatureFlagEntity existing, FeatureFlagEntity updated) {
    List<String> changedFields = new ArrayList<>();
    if (existing.status() != updated.status()) {
      changedFields.add("status");
    }
    if (existing.rolloutPercentage() != updated.rolloutPercentage()) {
      changedFields.add("rolloutPercentage");
    }
    if (!targetEnvironmentValues(existing).equals(targetEnvironmentValues(updated))) {
      changedFields.add("targetEnvironments");
    }
    if (!tenantAllowlistValues(existing).equals(tenantAllowlistValues(updated))) {
      changedFields.add("tenantAllowlist");
    }
    if (existing.killSwitchActive() != updated.killSwitchActive()) {
      changedFields.add("killSwitchActive");
    }
    return changedFields;
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
