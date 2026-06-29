package com.github.milez42.featureflags.approval;

import com.github.milez42.featureflags.flags.FeatureFlag;
import com.github.milez42.featureflags.policy.RiskReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Feature flag update approval workflow state.")
public record ApprovalRequestResponse(
    UUID approvalId,
    String flagKey,
    String requester,
    String approver,
    UpdateApprovalStatus status,
    FeatureFlag currentSnapshot,
    FeatureFlag proposedSnapshot,
    Set<RiskReason> riskReasons,
    String reason,
    Instant createdAt,
    Instant decidedAt,
    Instant usedAt) {}
