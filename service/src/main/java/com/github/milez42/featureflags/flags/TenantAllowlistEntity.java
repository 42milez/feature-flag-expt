package com.github.milez42.featureflags.flags;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("feature_flag_tenant_allowlist")
public record TenantAllowlistEntity(
        @Column("tenant_id") String tenantId
) {
}
