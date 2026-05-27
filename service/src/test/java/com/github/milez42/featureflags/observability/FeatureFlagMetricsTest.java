package com.github.milez42.featureflags.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.milez42.featureflags.flags.EvaluationReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class FeatureFlagMetricsTest {
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final FeatureFlagMetrics metrics = new FeatureFlagMetrics(meterRegistry);

  @Test
  void recordEvaluationIncrementsAggregateAndEnabledCounter() {
    metrics.recordEvaluation(
        "checkout-redesign", "production", true, EvaluationReason.ROLLOUT_MATCH);

    assertThat(
            meterRegistry
                .counter(
                    "feature.flag.evaluation",
                    "flag.key",
                    "checkout-redesign",
                    "environment",
                    "production",
                    "reason",
                    "ROLLOUT_MATCH")
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry
                .counter(
                    "feature.flag.evaluation.enabled",
                    "flag.key",
                    "checkout-redesign",
                    "environment",
                    "production",
                    "reason",
                    "ROLLOUT_MATCH")
                .count())
        .isEqualTo(1.0);
    assertThat(meterRegistry.find("feature.flag.evaluation.disabled").counter()).isNull();
  }

  @Test
  void recordEvaluationIncrementsAggregateAndDisabledCounter() {
    metrics.recordEvaluation(
        "checkout-redesign", "production", false, EvaluationReason.KILL_SWITCH_ACTIVE);

    assertThat(meterRegistry.find("feature.flag.evaluation").counter().count()).isEqualTo(1.0);
    assertThat(meterRegistry.find("feature.flag.evaluation.disabled").counter().count())
        .isEqualTo(1.0);
    assertThat(meterRegistry.find("feature.flag.evaluation.enabled").counter()).isNull();
  }

  @Test
  void recordUpdateDoesNotRecordKillSwitchEnabled() {
    metrics.recordUpdate("checkout-redesign");

    assertThat(meterRegistry.find("feature.flag.update").counter().count()).isEqualTo(1.0);
    assertThat(meterRegistry.find("feature.flag.kill.switch.enabled").counter()).isNull();
  }

  @Test
  void recordKillSwitchEnabledIncrementsDedicatedCounter() {
    metrics.recordKillSwitchEnabled("checkout-redesign");

    assertThat(meterRegistry.find("feature.flag.kill.switch.enabled").counter().count())
        .isEqualTo(1.0);
  }
}
