package com.github.milez42.featureflags.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Audit event emitted when a feature flag changes.")
public record AuditEventResponse(
    @Schema(description = "Audit event identifier.", example = "42") Long id,
    @Schema(
            description = "Feature flag key associated with the event.",
            example = "checkout-redesign")
        String flagKey,
    @Schema(description = "Type of audit event.", example = "ROLLOUT_PERCENTAGE_CHANGED")
        AuditEventType eventType,
    @Schema(
            description = "Authenticated operator username that caused the event.",
            example = "featureflags-operator")
        String actor,
    @Schema(
            description = "Event-specific details. Shape depends on eventType.",
            type = "object",
            implementation = Object.class)
        Object details,
    @Schema(description = "Time the event occurred.", example = "2026-05-05T00:00:00Z")
        Instant occurredAt) {}
