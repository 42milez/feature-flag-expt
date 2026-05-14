package com.github.milez42.featureflags.preview

import com.github.milez42.featureflags.flags.Environment
import com.github.milez42.featureflags.flags.EvaluationReason
import com.github.milez42.featureflags.flags.FeatureFlagEvaluator
import com.github.milez42.featureflags.flags.FeatureFlagResponse
import com.github.milez42.featureflags.flags.FeatureFlagService
import com.github.milez42.featureflags.flags.FeatureFlagStatus
import com.github.milez42.featureflags.flags.ProposedFeatureFlagChange
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EvaluationPreviewServiceTest {
  private val featureFlagService = mockk<FeatureFlagService>()
  private val service = EvaluationPreviewService(featureFlagService, FeatureFlagEvaluator())

  @Test
  fun `rollout increase changes contexts from disabled to enabled`() {
    givenFlag(rolloutPercentage = 0)

    val response =
        service.preview(
            "checkout-redesign",
            request(
                proposedChange = ProposedFeatureFlagChange(rolloutPercentage = 100),
                sampleContexts =
                    listOf(
                        EvaluationPreviewContext("production", "tenant-a", "user-a"),
                        EvaluationPreviewContext("production", "tenant-b", "user-b"),
                    ),
            ),
        )

    assertThat(response.diffs).allSatisfy {
      assertThat(it.before.enabled).isFalse()
      assertThat(it.after.enabled).isTrue()
      assertThat(it.changed).isTrue()
    }
    assertThat(response.summary.changedCount).isEqualTo(2)
  }

  @Test
  fun `kill switch proposed change disables all sample contexts`() {
    givenFlag(rolloutPercentage = 100)

    val response =
        service.preview(
            "checkout-redesign",
            request(
                proposedChange = ProposedFeatureFlagChange(killSwitchActive = true),
                sampleContexts =
                    listOf(
                        EvaluationPreviewContext("production", "tenant-a", "user-a"),
                        EvaluationPreviewContext("production", "tenant-b", "user-b"),
                    ),
            ),
        )

    assertThat(response.diffs).allSatisfy {
      assertThat(it.before.enabled).isTrue()
      assertThat(it.after.enabled).isFalse()
      assertThat(it.after.reason).isEqualTo(EvaluationReason.KILL_SWITCH_ACTIVE)
    }
    assertThat(response.summary.enabledCount).isZero()
    assertThat(response.summary.disabledCount).isEqualTo(2)
  }

  @Test
  fun `tenant allowlist proposal changes only matching tenants`() {
    givenFlag(rolloutPercentage = 0)

    val response =
        service.preview(
            "checkout-redesign",
            request(
                proposedChange = ProposedFeatureFlagChange(tenantAllowlist = setOf("tenant-a")),
                sampleContexts =
                    listOf(
                        EvaluationPreviewContext("production", "tenant-a", "user-a"),
                        EvaluationPreviewContext("production", "tenant-b", "user-b"),
                    ),
            ),
        )

    assertThat(response.diffs).extracting<Boolean> { it.changed }.containsExactly(true, false)
    assertThat(response.diffs[0].after.reason).isEqualTo(EvaluationReason.TENANT_ALLOWLIST_MATCH)
    assertThat(response.summary.changedCount).isEqualTo(1)
  }

  @Test
  fun `unchanged contexts are still returned with changed false`() {
    givenFlag(rolloutPercentage = 100)

    val response =
        service.preview(
            "checkout-redesign",
            request(
                proposedChange = ProposedFeatureFlagChange(rolloutPercentage = 100),
                sampleContexts =
                    listOf(EvaluationPreviewContext("production", "tenant-a", "user-a")),
            ),
        )

    assertThat(response.diffs).hasSize(1)
    assertThat(response.diffs.single().changed).isFalse()
  }

  @Test
  fun `summary counts match returned diffs`() {
    givenFlag(rolloutPercentage = 0)

    val response =
        service.preview(
            "checkout-redesign",
            request(
                proposedChange = ProposedFeatureFlagChange(tenantAllowlist = setOf("tenant-a")),
                sampleContexts =
                    listOf(
                        EvaluationPreviewContext("production", "tenant-a", "user-a"),
                        EvaluationPreviewContext("production", "tenant-b", "user-b"),
                        EvaluationPreviewContext("staging", "tenant-a", "user-a"),
                    ),
            ),
        )

    assertThat(response.summary.sampleCount).isEqualTo(response.diffs.size)
    assertThat(response.summary.beforeEnabledCount)
        .isEqualTo(response.diffs.count { it.before.enabled })
    assertThat(response.summary.beforeDisabledCount)
        .isEqualTo(response.diffs.count { !it.before.enabled })
    assertThat(response.summary.enabledCount).isEqualTo(response.diffs.count { it.after.enabled })
    assertThat(response.summary.disabledCount).isEqualTo(response.diffs.count { !it.after.enabled })
    assertThat(response.summary.changedCount).isEqualTo(response.diffs.count { it.changed })
  }

  @Test
  fun `target environments are converted to string values`() {
    givenFlag(targetEnvironments = setOf("production"), rolloutPercentage = 100)

    val response =
        service.preview(
            "checkout-redesign",
            request(
                proposedChange =
                    ProposedFeatureFlagChange(targetEnvironments = setOf(Environment.STAGING)),
                sampleContexts = listOf(EvaluationPreviewContext("staging", "tenant-a", "user-a")),
            ),
        )

    assertThat(response.diffs.single().before.reason)
        .isEqualTo(EvaluationReason.ENVIRONMENT_NOT_TARGETED)
    assertThat(response.diffs.single().after.enabled).isTrue()
  }

  @Test
  fun `empty target environments proposal clears target environments`() {
    givenFlag(targetEnvironments = setOf("production"), rolloutPercentage = 100)

    val response =
        service.preview(
            "checkout-redesign",
            request(
                proposedChange = ProposedFeatureFlagChange(targetEnvironments = emptySet()),
                sampleContexts =
                    listOf(EvaluationPreviewContext("production", "tenant-a", "user-a")),
            ),
        )

    assertThat(response.diffs.single().before.enabled).isTrue()
    assertThat(response.diffs.single().after.enabled).isFalse()
    assertThat(response.diffs.single().after.reason)
        .isEqualTo(EvaluationReason.ENVIRONMENT_NOT_TARGETED)
    assertThat(response.diffs.single().changed).isTrue()
  }

  @Test
  fun `empty tenant allowlist proposal clears existing allowlist`() {
    givenFlag(tenantAllowlist = setOf("tenant-a"), rolloutPercentage = 0)

    val response =
        service.preview(
            "checkout-redesign",
            request(
                proposedChange = ProposedFeatureFlagChange(tenantAllowlist = emptySet()),
                sampleContexts =
                    listOf(EvaluationPreviewContext("production", "tenant-a", "user-a")),
            ),
        )

    assertThat(response.diffs.single().before.enabled).isTrue()
    assertThat(response.diffs.single().before.reason)
        .isEqualTo(EvaluationReason.TENANT_ALLOWLIST_MATCH)
    assertThat(response.diffs.single().after.enabled).isFalse()
    assertThat(response.diffs.single().changed).isTrue()
  }

  private fun request(
      proposedChange: ProposedFeatureFlagChange,
      sampleContexts: List<EvaluationPreviewContext>,
  ): EvaluationPreviewRequest = EvaluationPreviewRequest(proposedChange, sampleContexts)

  private fun givenFlag(
      targetEnvironments: Set<String> = setOf("production"),
      tenantAllowlist: Set<String> = emptySet(),
      rolloutPercentage: Int,
  ) {
    every { featureFlagService.get("checkout-redesign") } returns
        FeatureFlagResponse(
            "checkout-redesign",
            FeatureFlagStatus.ENABLED,
            targetEnvironments,
            false,
            tenantAllowlist,
            rolloutPercentage,
        )
  }
}
