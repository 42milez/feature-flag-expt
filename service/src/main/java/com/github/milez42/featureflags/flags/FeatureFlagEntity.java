package com.github.milez42.featureflags.flags;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.util.Objects;
import java.util.Set;

@Table("feature_flags")
public class FeatureFlagEntity implements Persistable<String> {
    @Id
    private final String flagKey;
    private final FeatureFlagStatus status;
    @MappedCollection(idColumn = "flag_key")
    private final Set<TargetEnvironmentEntity> targetEnvironments;
    @Column("kill_switch_active")
    private final boolean killSwitchActive;
    @MappedCollection(idColumn = "flag_key")
    private final Set<TenantAllowlistEntity> tenantAllowlist;
    private final int rolloutPercentage;
    @Transient
    private final boolean newEntity;

    public static FeatureFlagEntity create(
            String flagKey,
            FeatureFlagStatus status,
            Set<TargetEnvironmentEntity> targetEnvironments,
            boolean killSwitchActive,
            Set<TenantAllowlistEntity> tenantAllowlist,
            int rolloutPercentage
    ) {
        return new FeatureFlagEntity(
                flagKey,
                status,
                targetEnvironments,
                killSwitchActive,
                tenantAllowlist,
                rolloutPercentage,
                true
        );
    }

    @PersistenceCreator
    public FeatureFlagEntity(
            String flagKey,
            FeatureFlagStatus status,
            Set<TargetEnvironmentEntity> targetEnvironments,
            boolean killSwitchActive,
            Set<TenantAllowlistEntity> tenantAllowlist,
            int rolloutPercentage
    ) {
        this(flagKey, status, targetEnvironments, killSwitchActive, tenantAllowlist, rolloutPercentage, false);
    }

    private FeatureFlagEntity(
            String flagKey,
            FeatureFlagStatus status,
            Set<TargetEnvironmentEntity> targetEnvironments,
            boolean killSwitchActive,
            Set<TenantAllowlistEntity> tenantAllowlist,
            int rolloutPercentage,
            boolean newEntity
    ) {
        Objects.requireNonNull(flagKey, "flagKey must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(targetEnvironments, "targetEnvironments must not be null");
        Objects.requireNonNull(tenantAllowlist, "tenantAllowlist must not be null");

        this.flagKey = flagKey;
        this.status = status;
        this.targetEnvironments = Set.copyOf(targetEnvironments);
        this.killSwitchActive = killSwitchActive;
        this.tenantAllowlist = Set.copyOf(tenantAllowlist);
        this.rolloutPercentage = rolloutPercentage;
        this.newEntity = newEntity;
    }

    @Override
    public String getId() {
        return flagKey;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public String flagKey() {
        return flagKey;
    }

    public FeatureFlagStatus status() {
        return status;
    }

    public Set<TargetEnvironmentEntity> targetEnvironments() {
        return targetEnvironments;
    }

    public boolean killSwitchActive() {
        return killSwitchActive;
    }

    public Set<TenantAllowlistEntity> tenantAllowlist() {
        return tenantAllowlist;
    }

    public int rolloutPercentage() {
        return rolloutPercentage;
    }
}
