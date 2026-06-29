package com.github.milez42.featureflags.approval;

import com.github.milez42.featureflags.audit.AuditEventDetails;
import com.github.milez42.featureflags.audit.AuditEventService;
import com.github.milez42.featureflags.audit.AuditEventType;
import com.github.milez42.featureflags.audit.CurrentActorProvider;
import com.github.milez42.featureflags.flags.FeatureFlag;
import com.github.milez42.featureflags.flags.FeatureFlagNotFoundException;
import com.github.milez42.featureflags.flags.FeatureFlagRepository;
import com.github.milez42.featureflags.flags.FeatureFlagUpdateResolver;
import com.github.milez42.featureflags.flags.TargetEnvironmentEntity;
import com.github.milez42.featureflags.flags.TenantAllowlistEntity;
import com.github.milez42.featureflags.policy.ApprovalState;
import com.github.milez42.featureflags.policy.PolicyActor;
import com.github.milez42.featureflags.policy.RiskAssessment;
import com.github.milez42.featureflags.policy.RolloutPolicyContext;
import com.github.milez42.featureflags.policy.RolloutPolicyValidationResult;
import com.github.milez42.featureflags.policy.RolloutPolicyValidator;
import com.github.milez42.featureflags.policy.RolloutPolicyViolation;
import com.github.milez42.featureflags.policy.RolloutPolicyViolationException;
import com.github.milez42.featureflags.policy.RolloutRiskClassifier;
import com.github.milez42.featureflags.policy.Severity;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateApprovalService {
  private static final String FLAG_APPROVER = "FLAG_APPROVER";
  private static final String HIGH_RISK_REQUIRES_APPROVAL = "HIGH_RISK_REQUIRES_APPROVAL";

  private final UpdateApprovalRepository approvalRepository;
  private final FeatureFlagRepository featureFlagRepository;
  private final FeatureFlagUpdateResolver updateResolver;
  private final RolloutRiskClassifier riskClassifier;
  private final RolloutPolicyValidator policyValidator;
  private final CurrentActorProvider currentActorProvider;
  private final AuditEventService auditEventService;
  private final Clock clock;

  public UpdateApprovalService(
      UpdateApprovalRepository approvalRepository,
      FeatureFlagRepository featureFlagRepository,
      FeatureFlagUpdateResolver updateResolver,
      RolloutRiskClassifier riskClassifier,
      RolloutPolicyValidator policyValidator,
      CurrentActorProvider currentActorProvider,
      AuditEventService auditEventService,
      Clock clock) {
    this.approvalRepository = approvalRepository;
    this.featureFlagRepository = featureFlagRepository;
    this.updateResolver = updateResolver;
    this.riskClassifier = riskClassifier;
    this.policyValidator = policyValidator;
    this.currentActorProvider = currentActorProvider;
    this.auditEventService = auditEventService;
    this.clock = clock;
  }

  @Transactional
  public ApprovalRequestResponse requestApproval(
      String flagKey, RequestUpdateApprovalRequest request) {
    FeatureFlag current = updateResolver.normalizeSnapshot(toDomain(findFlag(flagKey)));
    FeatureFlag proposed =
        updateResolver.normalizeSnapshot(updateResolver.resolve(current, request));
    RiskAssessment risk = riskClassifier.classify(current, proposed);

    if (!risk.requiresApproval()) {
      throw new RolloutPolicyViolationException(
          RolloutPolicyValidationResult.from(
              current.flagKey(),
              List.of(
                  new RolloutPolicyViolation(
                      "APPROVAL_NOT_REQUIRED",
                      "Low-risk changes do not require approval.",
                      Severity.ERROR))));
    }

    RolloutPolicyValidationResult policyResult =
        policyValidator.validate(
            current,
            proposed,
            new RolloutPolicyContext(
                risk, new ApprovalState.RequiredButMissing(), request.reason()));
    RolloutPolicyValidationResult blockingResult = withoutApprovalRequired(policyResult);
    if (!blockingResult.allowed()) {
      throw new RolloutPolicyViolationException(blockingResult);
    }

    String requester = currentActorProvider.currentActor();
    UpdateApprovalEntity saved =
        approvalRepository.save(
            UpdateApprovalEntity.create(
                current.flagKey(),
                requester,
                current,
                proposed,
                new RiskReasonSet(risk.reasons()),
                request.reason(),
                Instant.now(clock)));
    auditEventService.record(
        saved.flagKey(),
        AuditEventType.APPROVAL_REQUESTED,
        new AuditEventDetails.ApprovalRequestedDetails(
            saved.id(), requester, saved.riskReasons().values(), saved.reason()));
    return toResponse(saved);
  }

  @Transactional
  public ApprovalRequestResponse approve(String flagKey, UUID approvalId) {
    return decide(flagKey, approvalId, true);
  }

  @Transactional
  public ApprovalRequestResponse reject(String flagKey, UUID approvalId) {
    return decide(flagKey, approvalId, false);
  }

  @Transactional(readOnly = true)
  public ApprovalRequestResponse get(String flagKey, UUID approvalId) {
    UpdateApprovalEntity approval = findApproval(flagKey, approvalId);
    PolicyActor actor = currentActorProvider.currentPolicyActor();
    if (!actor.username().equals(approval.requester()) && !actor.hasRole(FLAG_APPROVER)) {
      throw ApprovalWorkflowException.notFound();
    }
    return toResponse(approval);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public ApprovalState approvalStateForUpdate(
      String flagKey,
      String requester,
      FeatureFlag current,
      FeatureFlag proposed,
      UUID approvalId) {
    if (approvalId == null) {
      return new ApprovalState.RequiredButMissing();
    }
    return approvalRepository
        .findById(approvalId)
        .filter(approval -> isConsumable(approval, flagKey, requester, current, proposed))
        .<ApprovalState>map(
            approval -> new ApprovalState.Verified(approval.id(), approval.approver()))
        .orElseGet(ApprovalState.RequiredButMissing::new);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void consumeVerifiedApprovalForUpdate(
      String flagKey,
      String requester,
      FeatureFlag current,
      FeatureFlag proposed,
      UUID approvalId) {
    UpdateApprovalEntity approval =
        approvalRepository
            .findById(approvalId)
            .filter(entity -> isConsumable(entity, flagKey, requester, current, proposed))
            .orElseThrow(() -> new InvalidApprovalForUpdateException("Approval is not consumable"));
    try {
      UpdateApprovalEntity saved = approvalRepository.save(approval.use(Instant.now(clock)));
      auditEventService.record(
          saved.flagKey(),
          AuditEventType.APPROVAL_USED,
          new AuditEventDetails.ApprovalUsedDetails(
              saved.id(), saved.requester(), saved.approver()));
    } catch (OptimisticLockingFailureException ex) {
      throw new InvalidApprovalForUpdateException("Approval was already consumed");
    }
  }

  private ApprovalRequestResponse decide(String flagKey, UUID approvalId, boolean approved) {
    PolicyActor actor = currentActorProvider.currentPolicyActor();
    if (!actor.hasRole(FLAG_APPROVER)) {
      throw ApprovalWorkflowException.forbidden("Approver role is required");
    }
    UpdateApprovalEntity approval = findApproval(flagKey, approvalId);
    if (actor.username().equals(approval.requester())) {
      throw ApprovalWorkflowException.forbidden("Approvers cannot approve their own request");
    }
    if (approval.status() != UpdateApprovalStatus.PENDING) {
      throw ApprovalWorkflowException.conflict("Approval request has already been decided");
    }

    try {
      UpdateApprovalEntity saved =
          approvalRepository.save(
              approved
                  ? approval.approve(actor.username(), Instant.now(clock))
                  : approval.reject(actor.username(), Instant.now(clock)));
      auditEventService.record(
          saved.flagKey(),
          approved ? AuditEventType.APPROVAL_APPROVED : AuditEventType.APPROVAL_REJECTED,
          approved
              ? new AuditEventDetails.ApprovalApprovedDetails(
                  saved.id(), saved.requester(), saved.approver())
              : new AuditEventDetails.ApprovalRejectedDetails(
                  saved.id(), saved.requester(), saved.approver()));
      return toResponse(saved);
    } catch (OptimisticLockingFailureException ex) {
      throw ApprovalWorkflowException.conflict("Approval request has already been decided");
    }
  }

  private boolean isConsumable(
      UpdateApprovalEntity approval,
      String flagKey,
      String requester,
      FeatureFlag current,
      FeatureFlag proposed) {
    return approval.status() == UpdateApprovalStatus.APPROVED
        && approval.flagKey().equals(flagKey)
        && approval.requester().equals(requester)
        && approval.currentSnapshot().equals(updateResolver.normalizeSnapshot(current))
        && approval.proposedSnapshot().equals(updateResolver.normalizeSnapshot(proposed));
  }

  private UpdateApprovalEntity findApproval(String flagKey, UUID approvalId) {
    return approvalRepository
        .findById(approvalId)
        .filter(approval -> approval.flagKey().equals(flagKey))
        .orElseThrow(ApprovalWorkflowException::notFound);
  }

  private com.github.milez42.featureflags.flags.FeatureFlagEntity findFlag(String flagKey) {
    return featureFlagRepository
        .findById(flagKey)
        .orElseThrow(() -> new FeatureFlagNotFoundException(flagKey));
  }

  private FeatureFlag toDomain(com.github.milez42.featureflags.flags.FeatureFlagEntity entity) {
    return new FeatureFlag(
        entity.flagKey(),
        entity.status(),
        entity.targetEnvironments().stream()
            .map(TargetEnvironmentEntity::environment)
            .collect(Collectors.toCollection(LinkedHashSet::new)),
        entity.killSwitchActive(),
        entity.tenantAllowlist().stream()
            .map(TenantAllowlistEntity::tenantId)
            .collect(Collectors.toCollection(LinkedHashSet::new)),
        entity.rolloutPercentage());
  }

  private ApprovalRequestResponse toResponse(UpdateApprovalEntity approval) {
    return new ApprovalRequestResponse(
        approval.id(),
        approval.flagKey(),
        approval.requester(),
        approval.approver(),
        approval.status(),
        approval.currentSnapshot(),
        approval.proposedSnapshot(),
        approval.riskReasons().values(),
        approval.reason(),
        approval.createdAt(),
        approval.decidedAt(),
        approval.usedAt());
  }

  private RolloutPolicyValidationResult withoutApprovalRequired(
      RolloutPolicyValidationResult result) {
    List<RolloutPolicyViolation> blockingViolations =
        result.violations().stream()
            .filter(violation -> !HIGH_RISK_REQUIRES_APPROVAL.equals(violation.code()))
            .toList();
    return RolloutPolicyValidationResult.from(result.flagKey(), blockingViolations);
  }
}
