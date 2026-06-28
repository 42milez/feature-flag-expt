package com.github.milez42.featureflags.policy;

import com.github.milez42.featureflags.flags.Environment;
import com.github.milez42.featureflags.flags.FeatureFlag;
import com.github.milez42.featureflags.flags.FeatureFlagStatus;

final class ProductionExposure {
  private ProductionExposure() {}

  static boolean canServeProductionWithoutAllowlist(FeatureFlag flag) {
    return flag.status() == FeatureFlagStatus.ENABLED
        && flag.targetEnvironments().contains(Environment.PRODUCTION.value())
        && !flag.killSwitchActive()
        && flag.tenantAllowlist().isEmpty();
  }
}
