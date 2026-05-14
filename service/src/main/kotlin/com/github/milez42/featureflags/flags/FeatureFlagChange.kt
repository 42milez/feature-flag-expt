package com.github.milez42.featureflags.flags

fun FeatureFlagResponse.toDomainFlag(): FeatureFlag =
    FeatureFlag(
        flagKey(),
        status(),
        targetEnvironments(),
        killSwitchActive(),
        tenantAllowlist(),
        rolloutPercentage(),
    )

/**
 * Applies proposed API-level changes to a domain flag.
 *
 * Proposed target environments are API enum values and must be normalized to the domain wire values
 * with [Environment.value].
 */
fun FeatureFlag.withProposedChange(change: ProposedFeatureFlagChange): FeatureFlag =
    FeatureFlag(
        flagKey(),
        change.status ?: status(),
        change.targetEnvironments?.map { it.value() }?.toSet() ?: targetEnvironments(),
        change.killSwitchActive ?: killSwitchActive(),
        change.tenantAllowlist ?: tenantAllowlist(),
        change.rolloutPercentage ?: rolloutPercentage(),
    )
