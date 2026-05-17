package com.github.milez42.featureflags.policy

import com.github.milez42.featureflags.flags.FeatureFlagResponse
import com.github.milez42.featureflags.flags.FeatureFlagService
import com.github.milez42.featureflags.flags.FeatureFlagStatus
import com.github.milez42.featureflags.flags.ProposedFeatureFlagChange
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RolloutPolicyValidationServiceTest {
  private val featureFlagService = mockk<FeatureFlagService>()
  private val service = RolloutPolicyValidationService(featureFlagService, RolloutPolicyValidator())

  @Test
  fun `validate returns response dto mapped from validator result`() {
    every { featureFlagService.get("checkout-redesign") } returns
        FeatureFlagResponse(
            "checkout-redesign",
            FeatureFlagStatus.ENABLED,
            setOf("production"),
            false,
            emptySet(),
            0,
        )

    val response =
        service.validate(
            "checkout-redesign",
            RolloutPolicyValidationRequest(
                proposedChange = ProposedFeatureFlagChange(rolloutPercentage = 100),
                highRisk = true,
                approvalGranted = false,
            ),
        )

    assertThat(response).isInstanceOf(RolloutPolicyValidationResponse::class.java)
    assertThat(response.flagKey()).isEqualTo("checkout-redesign")
    assertThat(response.allowed()).isFalse()
    assertThat(response.violations())
        .extracting<String> { it.code() }
        .containsExactly(
            "FULL_PRODUCTION_ROLLOUT",
            "HIGH_RISK_REQUIRES_APPROVAL",
            "PRODUCTION_ENABLEMENT_REQUIRES_REASON",
        )
  }
}
