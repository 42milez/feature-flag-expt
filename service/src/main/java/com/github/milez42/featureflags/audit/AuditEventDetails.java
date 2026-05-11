package com.github.milez42.featureflags.audit;

import com.github.milez42.featureflags.flags.FeatureFlagStatus;
import java.util.Set;

public sealed interface AuditEventDetails
    permits AuditEventDetails.FlagCreatedDetails,
        AuditEventDetails.FlagEnabledDetails,
        AuditEventDetails.FlagDisabledDetails,
        AuditEventDetails.RolloutPercentageChangedDetails,
        AuditEventDetails.TargetEnvironmentsChangedDetails,
        AuditEventDetails.TenantAllowlistChangedDetails,
        AuditEventDetails.KillSwitchEnabledDetails,
        AuditEventDetails.KillSwitchDisabledDetails {
  record FlagCreatedDetails(
      FeatureFlagStatus status,
      int rolloutPercentage,
      boolean killSwitchActive,
      Set<String> targetEnvironments,
      Set<String> tenantAllowlist)
      implements AuditEventDetails {}

  record FlagEnabledDetails(String field, FeatureFlagStatus oldValue, FeatureFlagStatus newValue)
      implements AuditEventDetails {}

  record FlagDisabledDetails(String field, FeatureFlagStatus oldValue, FeatureFlagStatus newValue)
      implements AuditEventDetails {}

  record RolloutPercentageChangedDetails(String field, int oldValue, int newValue)
      implements AuditEventDetails {}

  record TargetEnvironmentsChangedDetails(String field, Set<String> oldValue, Set<String> newValue)
      implements AuditEventDetails {}

  record TenantAllowlistChangedDetails(String field, Set<String> oldValue, Set<String> newValue)
      implements AuditEventDetails {}

  record KillSwitchEnabledDetails(String field, boolean oldValue, boolean newValue)
      implements AuditEventDetails {}

  record KillSwitchDisabledDetails(String field, boolean oldValue, boolean newValue)
      implements AuditEventDetails {}
}
