package com.github.milez42.featureflags.policy

import com.github.milez42.featureflags.flags.Environment
import com.github.milez42.featureflags.flags.FeatureFlagResponse
import com.github.milez42.featureflags.flags.FeatureFlagService
import com.github.milez42.featureflags.flags.FeatureFlagStatus
import com.github.milez42.featureflags.flags.ProposedFeatureFlagChange
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RolloutPolicyValidatorTest {
  private val featureFlagService = mockk<FeatureFlagService>()
  private val validator = RolloutPolicyValidator(featureFlagService)

  @Test
  fun `zero to full production rollout is rejected`() {
    givenFlag(rolloutPercentage = 0, killSwitchActive = true)

    val result = validate(ProposedFeatureFlagChange(rolloutPercentage = 100))

    assertThat(result.violations)
        .extracting<String> { it.code }
        .containsExactly("FULL_PRODUCTION_ROLLOUT")
    assertInvariant(result)
  }

  @Test
  fun `non-production zero to full rollout is allowed`() {
    givenFlag(targetEnvironments = setOf("staging"), rolloutPercentage = 0)

    val result = validate(ProposedFeatureFlagChange(rolloutPercentage = 100))

    assertThat(result.allowed).isTrue()
    assertThat(result.violations).isEmpty()
    assertInvariant(result)
  }

  @Test
  fun `partial to full production rollout is allowed by phase six full rollout policy`() {
    givenFlag(rolloutPercentage = 50, killSwitchActive = true)

    val result = validate(ProposedFeatureFlagChange(rolloutPercentage = 100))

    assertThat(result.allowed).isTrue()
    assertThat(result.violations).isEmpty()
    assertInvariant(result)
  }

  @Test
  fun `production rollout without kill switch is rejected`() {
    givenFlag(status = FeatureFlagStatus.DISABLED, targetEnvironments = setOf("staging"))

    val result =
        validate(
            ProposedFeatureFlagChange(
                targetEnvironments = setOf(Environment.PRODUCTION),
                killSwitchActive = false,
            )
        )

    assertThat(result.violations)
        .extracting<String> { it.code }
        .containsExactly("PRODUCTION_WITHOUT_KILL_SWITCH")
    assertInvariant(result)
  }

  @Test
  fun `production rollout at zero percent without kill switch is rejected`() {
    givenFlag(status = FeatureFlagStatus.DISABLED, rolloutPercentage = 0, killSwitchActive = false)

    val result = validate(ProposedFeatureFlagChange(rolloutPercentage = 0))

    assertThat(result.violations)
        .extracting<String> { it.code }
        .containsExactly("PRODUCTION_WITHOUT_KILL_SWITCH")
    assertInvariant(result)
  }

  @Test
  fun `high-risk change without approval is rejected`() {
    givenFlag(targetEnvironments = setOf("staging"))

    val result =
        validator.validate(
            "checkout-redesign",
            RolloutPolicyValidationRequest(
                proposedChange = ProposedFeatureFlagChange(rolloutPercentage = 50),
                highRisk = true,
                approvalGranted = false,
            ),
        )

    assertThat(result.violations)
        .extracting<String> { it.code }
        .containsExactly("HIGH_RISK_REQUIRES_APPROVAL")
    assertInvariant(result)
  }

  @Test
  fun `production enablement without allowlist requires non-blank reason`() {
    givenFlag(
        status = FeatureFlagStatus.DISABLED,
        tenantAllowlist = emptySet(),
        rolloutPercentage = 0,
        killSwitchActive = false,
    )

    val result =
        validator.validate(
            "checkout-redesign",
            RolloutPolicyValidationRequest(
                proposedChange = ProposedFeatureFlagChange(status = FeatureFlagStatus.ENABLED),
                reason = " ",
            ),
        )

    assertThat(result.violations)
        .extracting<String> { it.code }
        .contains(
            "PRODUCTION_WITHOUT_KILL_SWITCH",
            "PRODUCTION_ENABLEMENT_REQUIRES_REASON",
        )
    assertInvariant(result)
  }

  @Test
  fun `production enablement without allowlist allows valid reason`() {
    givenFlag(
        status = FeatureFlagStatus.DISABLED,
        tenantAllowlist = emptySet(),
        rolloutPercentage = 0,
        killSwitchActive = false,
    )

    val result =
        validator.validate(
            "checkout-redesign",
            RolloutPolicyValidationRequest(
                proposedChange = ProposedFeatureFlagChange(status = FeatureFlagStatus.ENABLED),
                reason = "valid business justification",
            ),
        )

    assertThat(result.violations)
        .extracting<String> { it.code }
        .contains("PRODUCTION_WITHOUT_KILL_SWITCH")
        .doesNotContain("PRODUCTION_ENABLEMENT_REQUIRES_REASON")
    assertInvariant(result)
  }

  @Test
  fun `multiple violations are returned together`() {
    givenFlag(rolloutPercentage = 0, killSwitchActive = false, tenantAllowlist = emptySet())

    val result =
        validator.validate(
            "checkout-redesign",
            RolloutPolicyValidationRequest(
                proposedChange = ProposedFeatureFlagChange(rolloutPercentage = 100),
                highRisk = true,
                approvalGranted = false,
            ),
        )

    assertThat(result.violations)
        .extracting<String> { it.code }
        .containsExactlyInAnyOrder(
            "FULL_PRODUCTION_ROLLOUT",
            "PRODUCTION_WITHOUT_KILL_SWITCH",
            "HIGH_RISK_REQUIRES_APPROVAL",
            "PRODUCTION_ENABLEMENT_REQUIRES_REASON",
        )
    assertInvariant(result)
  }

  @Test
  fun `safe staged rollout is allowed`() {
    givenFlag(targetEnvironments = setOf("staging"), rolloutPercentage = 10)

    val result = validate(ProposedFeatureFlagChange(rolloutPercentage = 25))

    assertThat(result.allowed).isTrue()
    assertThat(result.violations).isEmpty()
    assertInvariant(result)
  }

  @Test
  fun `empty target environments proposal clears target environments`() {
    givenFlag(status = FeatureFlagStatus.DISABLED, killSwitchActive = false)

    val result =
        validate(ProposedFeatureFlagChange(targetEnvironments = emptySet(), rolloutPercentage = 50))

    assertThat(result.allowed).isTrue()
    assertThat(result.violations).isEmpty()
    assertInvariant(result)
  }

  @Test
  fun `empty tenant allowlist proposal clears tenant allowlist`() {
    givenFlag(tenantAllowlist = setOf("tenant-a"), killSwitchActive = false)

    val result = validate(ProposedFeatureFlagChange(tenantAllowlist = emptySet()))

    assertThat(result.violations)
        .extracting<String> { it.code }
        .contains("PRODUCTION_ENABLEMENT_REQUIRES_REASON")
    assertInvariant(result)
  }

  @Test
  fun `proposed target environments are normalized to domain wire strings`() {
    givenFlag(status = FeatureFlagStatus.DISABLED, targetEnvironments = setOf("staging"))

    val result =
        validate(
            ProposedFeatureFlagChange(
                targetEnvironments = setOf(Environment.PRODUCTION),
                killSwitchActive = false,
            )
        )

    assertThat(result.violations)
        .extracting<String> { it.code }
        .containsExactly("PRODUCTION_WITHOUT_KILL_SWITCH")
    assertInvariant(result)
  }

  private fun validate(change: ProposedFeatureFlagChange): RolloutPolicyValidationResult =
      validator.validate(
          "checkout-redesign",
          RolloutPolicyValidationRequest(proposedChange = change),
      )

  private fun assertInvariant(result: RolloutPolicyValidationResult) {
    assertThat(result.allowed).isEqualTo(result.violations.isEmpty())
  }

  private fun givenFlag(
      status: FeatureFlagStatus = FeatureFlagStatus.ENABLED,
      targetEnvironments: Set<String> = setOf("production"),
      killSwitchActive: Boolean = false,
      tenantAllowlist: Set<String> = setOf("tenant-a"),
      rolloutPercentage: Int = 25,
  ) {
    every { featureFlagService.get("checkout-redesign") } returns
        FeatureFlagResponse(
            "checkout-redesign",
            status,
            targetEnvironments,
            killSwitchActive,
            tenantAllowlist,
            rolloutPercentage,
        )
  }
}
