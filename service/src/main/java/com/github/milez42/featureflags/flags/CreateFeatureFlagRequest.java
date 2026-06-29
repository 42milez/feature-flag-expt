package com.github.milez42.featureflags.flags;

import static com.github.milez42.featureflags.flags.FeatureFlagConstraints.FLAG_KEY_MAX_LENGTH;
import static com.github.milez42.featureflags.flags.FeatureFlagConstraints.FLAG_KEY_MIN_LENGTH;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(description = "Request to create a feature flag.")
public record CreateFeatureFlagRequest(
    @Schema(
            description = "Stable feature flag key.",
            example = "checkout-redesign",
            minLength = FLAG_KEY_MIN_LENGTH)
        @NotBlank
        @Size(max = FLAG_KEY_MAX_LENGTH)
        String flagKey,
    @Schema(description = "Initial flag status.", example = "ENABLED") @NotNull
        FeatureFlagStatus status,
    @Schema(
            description = "Environments where the flag may be evaluated.",
            example = "[\"production\", \"staging\"]")
        @NotNull
        @NotEmpty
        Set<Environment> targetEnvironments,
    @Schema(description = "Whether the emergency kill switch is active.", example = "false")
        @NotNull
        Boolean killSwitchActive,
    @Schema(description = "Tenant IDs that are always allowed.", example = "[\"tenant-a\"]")
        @Size(max = 1000)
        Set<@NotBlank @Size(max = 255) String> tenantAllowlist,
    @Schema(description = "Percentage rollout from 0 to 100.", example = "25")
        @NotNull
        @Min(0)
        @Max(100)
        Integer rolloutPercentage,
    @Schema(description = "Business reason for enabling production access without an allowlist.")
        @Size(max = 1000)
        String reason) {}
