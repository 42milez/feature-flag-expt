package com.github.milez42.featureflags.observability;

import com.github.milez42.featureflags.flags.EvaluationReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlagMetrics {
  private final MeterRegistry meterRegistry;

  public FeatureFlagMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordEvaluation(
      String flagKey, String environment, boolean enabled, EvaluationReason reason) {
    counter(
            "feature.flag.evaluation",
            "flag.key",
            flagKey,
            "environment",
            environment,
            "reason",
            reason.name())
        .increment();

    counter(
            enabled ? "feature.flag.evaluation.enabled" : "feature.flag.evaluation.disabled",
            "flag.key",
            flagKey,
            "environment",
            environment,
            "reason",
            reason.name())
        .increment();
  }

  public void recordUpdate(String flagKey) {
    counter("feature.flag.update", "flag.key", flagKey).increment();
  }

  public void recordKillSwitchEnabled(String flagKey) {
    counter("feature.flag.kill.switch.enabled", "flag.key", flagKey).increment();
  }

  private Counter counter(String name, String... tags) {
    return Counter.builder(name).tags(tags).register(meterRegistry);
  }
}
