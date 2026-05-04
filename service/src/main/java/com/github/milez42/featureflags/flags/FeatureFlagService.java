package com.github.milez42.featureflags.flags;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FeatureFlagService {
    private final FeatureFlagRepository repository;
    private final FeatureFlagEvaluator evaluator;

    public FeatureFlagService(FeatureFlagRepository repository, FeatureFlagEvaluator evaluator) {
        this.repository = repository;
        this.evaluator = evaluator;
    }

    @Transactional
    public FeatureFlagResponse create(CreateFeatureFlagRequest request) {
        String flagKey = normalizeRequired(request.flagKey(), "flagKey");
        if (repository.existsById(flagKey)) {
            throw new FeatureFlagDuplicateException(flagKey);
        }

        FeatureFlagEntity entity = FeatureFlagEntity.create(
                flagKey,
                request.status(),
                targetEnvironments(request.targetEnvironments()),
                Boolean.TRUE.equals(request.killSwitchActive()),
                tenantAllowlist(request.tenantAllowlist()),
                request.rolloutPercentage()
        );

        return toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public FeatureFlagResponse get(String flagKey) {
        return toResponse(findEntity(flagKey));
    }

    @Transactional
    public FeatureFlagResponse update(String flagKey, UpdateFeatureFlagRequest request) {
        FeatureFlagEntity existing = findEntity(flagKey);
        FeatureFlagEntity updated = new FeatureFlagEntity(
                existing.flagKey(),
                request.status() == null ? existing.status() : request.status(),
                request.targetEnvironments() == null
                        ? existing.targetEnvironments()
                        : targetEnvironments(request.targetEnvironments()),
                request.killSwitchActive() == null ? existing.killSwitchActive() : request.killSwitchActive(),
                request.tenantAllowlist() == null
                        ? existing.tenantAllowlist()
                        : tenantAllowlist(request.tenantAllowlist()),
                request.rolloutPercentage() == null ? existing.rolloutPercentage() : request.rolloutPercentage()
        );

        return toResponse(repository.save(updated));
    }

    @Transactional(readOnly = true)
    public EvaluateFeatureFlagResponse evaluate(EvaluateFeatureFlagRequest request) {
        FeatureFlagEntity entity = findEntity(request.flagKey());
        EvaluationResult result = evaluator.evaluate(
                toDomain(entity),
                new EvaluationContext(
                        normalizeRequired(request.environment(), "environment"),
                        normalizeOptional(request.tenantId()),
                        normalizeOptional(request.userId())
                )
        );

        return new EvaluateFeatureFlagResponse(
                entity.flagKey(),
                result.enabled(),
                result.reason(),
                result.bucket()
        );
    }

    private FeatureFlagEntity findEntity(String flagKey) {
        String normalizedFlagKey = normalizeRequired(flagKey, "flagKey");
        return repository.findById(normalizedFlagKey)
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
                entity.rolloutPercentage()
        );
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
                entity.rolloutPercentage()
        );
    }

    private Set<TargetEnvironmentEntity> targetEnvironments(Set<String> values) {
        return normalizeSet(values).stream()
                .map(TargetEnvironmentEntity::new)
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
