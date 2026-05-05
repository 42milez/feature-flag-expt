package com.github.milez42.featureflags.audit;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditEventService {
  private final AuditEventRepository repository;
  private final Clock clock;

  public AuditEventService(AuditEventRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(String flagKey, AuditEventType eventType, AuditEventDetails details) {
    repository.save(AuditEvent.newEvent(flagKey, eventType, details, Instant.now(clock)));
  }
}
