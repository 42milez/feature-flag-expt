package com.github.milez42.featureflags.approval;

import com.github.milez42.featureflags.flags.FeatureFlag;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

@Table("feature_flag_update_approvals")
public class UpdateApprovalEntity {
  @Id private final UUID id;
  private final String flagKey;
  private final String requester;
  private final String approver;
  private final UpdateApprovalStatus status;
  @Version private final Long version;
  private final FeatureFlag currentSnapshot;
  private final FeatureFlag proposedSnapshot;
  private final RiskReasonSet riskReasons;
  private final String reason;
  private final Instant createdAt;
  private final Instant decidedAt;
  private final Instant usedAt;

  public static UpdateApprovalEntity create(
      String flagKey,
      String requester,
      FeatureFlag currentSnapshot,
      FeatureFlag proposedSnapshot,
      RiskReasonSet riskReasons,
      String reason,
      Instant createdAt) {
    return new UpdateApprovalEntity(
        UUID.randomUUID(),
        flagKey,
        requester,
        null,
        UpdateApprovalStatus.PENDING,
        null,
        currentSnapshot,
        proposedSnapshot,
        riskReasons,
        reason,
        createdAt,
        null,
        null);
  }

  @PersistenceCreator
  public UpdateApprovalEntity(
      UUID id,
      String flagKey,
      String requester,
      String approver,
      UpdateApprovalStatus status,
      Long version,
      FeatureFlag currentSnapshot,
      FeatureFlag proposedSnapshot,
      RiskReasonSet riskReasons,
      String reason,
      Instant createdAt,
      Instant decidedAt,
      Instant usedAt) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.flagKey = requireText(flagKey, "flagKey");
    this.requester = requireText(requester, "requester");
    this.approver = approver;
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.version = version;
    this.currentSnapshot =
        Objects.requireNonNull(currentSnapshot, "currentSnapshot must not be null");
    this.proposedSnapshot =
        Objects.requireNonNull(proposedSnapshot, "proposedSnapshot must not be null");
    this.riskReasons = Objects.requireNonNull(riskReasons, "riskReasons must not be null");
    this.reason = reason;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    this.decidedAt = decidedAt;
    this.usedAt = usedAt;
  }

  public UpdateApprovalEntity approve(String approver, Instant decidedAt) {
    return decide(UpdateApprovalStatus.APPROVED, approver, decidedAt);
  }

  public UpdateApprovalEntity reject(String approver, Instant decidedAt) {
    return decide(UpdateApprovalStatus.REJECTED, approver, decidedAt);
  }

  public UpdateApprovalEntity use(Instant usedAt) {
    return new UpdateApprovalEntity(
        id,
        flagKey,
        requester,
        approver,
        UpdateApprovalStatus.USED,
        version,
        currentSnapshot,
        proposedSnapshot,
        riskReasons,
        reason,
        createdAt,
        decidedAt,
        usedAt);
  }

  private UpdateApprovalEntity decide(
      UpdateApprovalStatus nextStatus, String approver, Instant decidedAt) {
    return new UpdateApprovalEntity(
        id,
        flagKey,
        requester,
        requireText(approver, "approver"),
        nextStatus,
        version,
        currentSnapshot,
        proposedSnapshot,
        riskReasons,
        reason,
        createdAt,
        Objects.requireNonNull(decidedAt, "decidedAt must not be null"),
        usedAt);
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }

  public UUID id() {
    return id;
  }

  public String flagKey() {
    return flagKey;
  }

  public String requester() {
    return requester;
  }

  public String approver() {
    return approver;
  }

  public UpdateApprovalStatus status() {
    return status;
  }

  public Long version() {
    return version;
  }

  public FeatureFlag currentSnapshot() {
    return currentSnapshot;
  }

  public FeatureFlag proposedSnapshot() {
    return proposedSnapshot;
  }

  public RiskReasonSet riskReasons() {
    return riskReasons;
  }

  public String reason() {
    return reason;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant decidedAt() {
    return decidedAt;
  }

  public Instant usedAt() {
    return usedAt;
  }
}
