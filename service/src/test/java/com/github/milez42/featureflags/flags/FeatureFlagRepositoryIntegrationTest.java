package com.github.milez42.featureflags.flags;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FeatureFlagRepositoryIntegrationTest extends PostgreSqlIntegrationTest {
    @Autowired
    private FeatureFlagRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void deleteFlags() {
        repository.deleteAll();
    }

    @Test
    void flywayCreatesFeatureFlagTables() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'public'
                          and table_name in (
                              'feature_flags',
                              'feature_flag_target_environments',
                              'feature_flag_tenant_allowlist'
                          )
                        """,
                Integer.class
        );

        assertThat(tableCount).isEqualTo(3);
    }

    @Test
    void savesAndLoadsFlagWithChildCollections() {
        FeatureFlagEntity saved = repository.save(FeatureFlagEntity.create(
                "checkout-redesign",
                FeatureFlagStatus.ENABLED,
                Set.of(new TargetEnvironmentEntity("production"), new TargetEnvironmentEntity("staging")),
                false,
                Set.of(new TenantAllowlistEntry("tenant-a"), new TenantAllowlistEntry("tenant-b")),
                25
        ));

        assertThat(saved.flagKey()).isEqualTo("checkout-redesign");

        FeatureFlagEntity loaded = repository.findById("checkout-redesign").orElseThrow();
        assertThat(loaded.status()).isEqualTo(FeatureFlagStatus.ENABLED);
        assertThat(loaded.targetEnvironments())
                .extracting(TargetEnvironmentEntity::environment)
                .containsExactlyInAnyOrder("production", "staging");
        assertThat(loaded.tenantAllowlist())
                .extracting(TenantAllowlistEntry::tenantId)
                .containsExactlyInAnyOrder("tenant-a", "tenant-b");
        assertThat(loaded.rolloutPercentage()).isEqualTo(25);
    }

    @Test
    void savingExistingFlagReplacesChildCollections() {
        repository.save(FeatureFlagEntity.create(
                "checkout-redesign",
                FeatureFlagStatus.ENABLED,
                Set.of(new TargetEnvironmentEntity("production"), new TargetEnvironmentEntity("staging")),
                false,
                Set.of(new TenantAllowlistEntry("tenant-a"), new TenantAllowlistEntry("tenant-b")),
                25
        ));

        repository.save(new FeatureFlagEntity(
                "checkout-redesign",
                FeatureFlagStatus.DISABLED,
                Set.of(new TargetEnvironmentEntity("production")),
                true,
                Set.of(),
                0
        ));

        FeatureFlagEntity loaded = repository.findById("checkout-redesign").orElseThrow();
        assertThat(loaded.status()).isEqualTo(FeatureFlagStatus.DISABLED);
        assertThat(loaded.targetEnvironments())
                .extracting(TargetEnvironmentEntity::environment)
                .containsExactly("production");
        assertThat(loaded.tenantAllowlist()).isEmpty();
        assertThat(loaded.killSwitchActive()).isTrue();
        assertThat(loaded.rolloutPercentage()).isZero();
    }
}
