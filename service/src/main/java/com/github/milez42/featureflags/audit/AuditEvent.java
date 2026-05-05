package com.github.milez42.featureflags.audit;

import java.time.Instant;
import java.util.Objects;

public record AuditEvent(
    Long id,
    String flagKey,
    AuditEventType eventType,
    AuditEventDetails details,
    Instant occurredAt) {
  public AuditEvent {
    Objects.requireNonNull(flagKey, "flagKey must not be null");
    Objects.requireNonNull(eventType, "eventType must not be null");
    Objects.requireNonNull(details, "details must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }

  public static AuditEvent newEvent(
      String flagKey, AuditEventType eventType, AuditEventDetails details, Instant occurredAt) {
    return new AuditEvent(null, flagKey, eventType, details, occurredAt);
  }
}
