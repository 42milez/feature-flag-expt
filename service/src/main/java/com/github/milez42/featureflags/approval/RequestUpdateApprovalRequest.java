package com.github.milez42.featureflags.approval;

import com.github.milez42.featureflags.flags.Environment;
import com.github.milez42.featureflags.flags.FeatureFlagStatus;
import com.github.milez42.featureflags.flags.FeatureFlagUpdateProposal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(description = "Request approval for a proposed feature flag update.")
public record RequestUpdateApprovalRequest(
    @Schema(description = "Proposed flag status.", example = "ENABLED") FeatureFlagStatus status,
    @Schema(
            description =
                "Replacement target environments. Omit or send null to preserve the current"
                    + " value; send an empty array to clear target environments.",
            nullable = true,
            example = "[\"production\"]")
        Set<Environment> targetEnvironments,
    @Schema(description = "Proposed kill switch state.", example = "false")
        Boolean killSwitchActive,
    @Schema(
            description =
                "Replacement tenant allowlist. Omit or send null to preserve the current value;"
                    + " send an empty array to clear the allowlist.",
            nullable = true,
            example = "[\"tenant-a\", \"tenant-b\"]")
        @Size(max = 1000)
        Set<@NotBlank @Size(max = 255) String> tenantAllowlist,
    @Schema(description = "Proposed percentage rollout from 0 to 100.", example = "50")
        @Min(0)
        @Max(100)
        Integer rolloutPercentage,
    @Schema(description = "Business reason for enabling production access without an allowlist.")
        @Size(max = 1000)
        String reason)
    implements FeatureFlagUpdateProposal {}
