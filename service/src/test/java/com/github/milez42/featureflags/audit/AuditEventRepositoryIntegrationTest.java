package com.github.milez42.featureflags.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.milez42.featureflags.flags.FeatureFlagStatus;
import com.github.milez42.featureflags.support.PostgreSqlIntegrationTest;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class AuditEventRepositoryIntegrationTest extends PostgreSqlIntegrationTest {
  @Autowired private AuditEventRepository repository;

  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void deleteAuditEvents() {
    jdbcClient.sql("delete from audit_events").update();
  }

  @Test
  void flywayCreatesAuditEventsTable() {
    Integer tableCount =
        jdbcClient
            .sql(
                """
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name = 'audit_events'
                """)
            .query(Integer.class)
            .single();

    assertThat(tableCount).isEqualTo(1);
  }

  @Test
  void savesAndLoadsJsonbDetailsByFlagKey() {
    Instant occurredAt = Instant.parse("2026-05-05T00:00:00Z");
    repository.save(
        AuditEvent.newEvent(
            "checkout-redesign",
            AuditEventType.FLAG_CREATED,
            new AuditEventDetails.FlagCreatedDetails(
                FeatureFlagStatus.ENABLED, 50, false, Set.of("production"), Set.of("tenant-a")),
            occurredAt));

    assertThat(repository.findByFlagKey("checkout-redesign"))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.id()).isNotNull();
              assertThat(event.eventType()).isEqualTo(AuditEventType.FLAG_CREATED);
              assertThat(event.occurredAt()).isEqualTo(occurredAt);
              assertThat(event.details())
                  .isEqualTo(
                      new AuditEventDetails.FlagCreatedDetails(
                          FeatureFlagStatus.ENABLED,
                          50,
                          false,
                          Set.of("production"),
                          Set.of("tenant-a")));
            });
  }

  @Test
  void readsOnlyEventsForRequestedFlagKeyInInsertOrder() {
    Instant occurredAt = Instant.parse("2026-05-05T00:00:00Z");
    repository.save(
        AuditEvent.newEvent(
            "checkout-redesign",
            AuditEventType.ROLLOUT_PERCENTAGE_CHANGED,
            new AuditEventDetails.RolloutPercentageChangedDetails("rolloutPercentage", 25, 50),
            occurredAt));
    repository.save(
        AuditEvent.newEvent(
            "profile-page",
            AuditEventType.KILL_SWITCH_ENABLED,
            new AuditEventDetails.KillSwitchEnabledDetails("killSwitchActive", false, true),
            occurredAt));
    repository.save(
        AuditEvent.newEvent(
            "checkout-redesign",
            AuditEventType.TENANT_ALLOWLIST_CHANGED,
            new AuditEventDetails.TenantAllowlistChangedDetails(
                "tenantAllowlist", Set.of("tenant-a"), Set.of("tenant-b")),
            occurredAt));

    assertThat(repository.findByFlagKey("checkout-redesign"))
        .extracting(AuditEvent::eventType)
        .containsExactly(
            AuditEventType.ROLLOUT_PERCENTAGE_CHANGED, AuditEventType.TENANT_ALLOWLIST_CHANGED);
  }
}
