package com.github.milez42.featureflags.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditEventService {
  private final AuditEventRepository repository;
  private final CurrentActorProvider currentActorProvider;
  private final Clock clock;

  public AuditEventService(
      AuditEventRepository repository, CurrentActorProvider currentActorProvider, Clock clock) {
    this.repository = repository;
    this.currentActorProvider = currentActorProvider;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(String flagKey, AuditEventType eventType, AuditEventDetails details) {
    repository.save(
        AuditEvent.newEvent(
            flagKey, eventType, currentActorProvider.currentActor(), details, Instant.now(clock)));
  }

  @Transactional(readOnly = true)
  public List<AuditEvent> findByFlagKey(String flagKey) {
    return repository.findByFlagKey(flagKey);
  }
}
