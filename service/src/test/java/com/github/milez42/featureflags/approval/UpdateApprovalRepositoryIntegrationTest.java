package com.github.milez42.featureflags.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.milez42.featureflags.flags.FeatureFlag;
import com.github.milez42.featureflags.flags.FeatureFlagStatus;
import com.github.milez42.featureflags.policy.RiskReason;
import com.github.milez42.featureflags.support.PostgreSqlIntegrationTest;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class UpdateApprovalRepositoryIntegrationTest extends PostgreSqlIntegrationTest {
  @Autowired private UpdateApprovalRepository repository;

  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void deleteApprovals() {
    jdbcClient.sql("delete from feature_flag_update_approvals").update();
  }

  @Test
  void flywayCreatesUpdateApprovalTable() {
    Integer tableCount =
        jdbcClient
            .sql(
                """
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name = 'feature_flag_update_approvals'
                """)
            .query(Integer.class)
            .single();

    assertThat(tableCount).isEqualTo(1);
  }

  @Test
  void savesAndLoadsJsonbSnapshotsAndRiskReasons() {
    UpdateApprovalEntity saved =
        repository.save(
            UpdateApprovalEntity.create(
                "checkout-redesign",
                "test-operator",
                currentFlag(0),
                currentFlag(80),
                new RiskReasonSet(Set.of(RiskReason.LARGE_PRODUCTION_ROLLOUT_INCREASE)),
                "Roll out to approved tenants.",
                Instant.parse("2026-06-01T00:00:00Z")));

    assertThat(repository.findById(saved.id()))
        .get()
        .satisfies(
            approval -> {
              assertThat(approval.version()).isZero();
              assertThat(approval.currentSnapshot()).isEqualTo(currentFlag(0));
              assertThat(approval.proposedSnapshot()).isEqualTo(currentFlag(80));
              assertThat(approval.riskReasons().values())
                  .containsExactly(RiskReason.LARGE_PRODUCTION_ROLLOUT_INCREASE);
            });
  }

  @Test
  void versionIncrementsAndRejectsStaleSaves() {
    UpdateApprovalEntity saved =
        repository.save(
            UpdateApprovalEntity.create(
                "checkout-redesign",
                "test-operator",
                currentFlag(0),
                currentFlag(80),
                new RiskReasonSet(Set.of(RiskReason.LARGE_PRODUCTION_ROLLOUT_INCREASE)),
                null,
                Instant.parse("2026-06-01T00:00:00Z")));
    UpdateApprovalEntity firstCopy = repository.findById(saved.id()).orElseThrow();
    UpdateApprovalEntity secondCopy = repository.findById(saved.id()).orElseThrow();

    UpdateApprovalEntity approved =
        repository.save(firstCopy.approve("test-approver", Instant.parse("2026-06-01T01:00:00Z")));

    assertThat(approved.version()).isEqualTo(1L);
    assertThatThrownBy(
            () ->
                repository.save(
                    secondCopy.reject("other-approver", Instant.parse("2026-06-01T01:00:01Z"))))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  private FeatureFlag currentFlag(int rolloutPercentage) {
    return new FeatureFlag(
        "checkout-redesign",
        FeatureFlagStatus.ENABLED,
        Set.of("production"),
        false,
        Set.of("tenant-a"),
        rolloutPercentage);
  }
}
