package com.github.milez42.featureflags.flags;

import java.util.Set;

public interface FeatureFlagUpdateProposal {
  FeatureFlagStatus status();

  Set<Environment> targetEnvironments();

  Boolean killSwitchActive();

  Set<String> tenantAllowlist();

  Integer rolloutPercentage();

  String reason();
}
