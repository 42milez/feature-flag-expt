package com.github.milez42.featureflags.approval;

import static com.github.milez42.featureflags.flags.FeatureFlagConstraints.FLAG_KEY_MAX_LENGTH;
import static com.github.milez42.featureflags.flags.FeatureFlagConstraints.FLAG_KEY_MIN_LENGTH;

import com.github.milez42.featureflags.policy.RolloutPolicyValidationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flags/{flagKey}/approval-requests")
@Tag(name = "Update Approvals", description = "High-risk feature flag update approval APIs.")
@Validated
public class UpdateApprovalController {
  private final UpdateApprovalService service;

  public UpdateApprovalController(UpdateApprovalService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(summary = "Request approval for a feature flag update")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Approval request created",
        content = @Content(schema = @Schema(implementation = ApprovalRequestResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Feature flag not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description = "Rollout policy violation",
        content =
            @Content(schema = @Schema(implementation = RolloutPolicyValidationResponse.class)))
  })
  public ResponseEntity<ApprovalRequestResponse> requestApproval(
      @Parameter(
              description = "Feature flag key.",
              example = "checkout-redesign",
              schema = @Schema(minLength = FLAG_KEY_MIN_LENGTH, maxLength = FLAG_KEY_MAX_LENGTH))
          @PathVariable
          @NotBlank
          @Size(max = FLAG_KEY_MAX_LENGTH)
          String flagKey,
      @Valid @RequestBody RequestUpdateApprovalRequest request) {
    ApprovalRequestResponse response = service.requestApproval(flagKey, request);
    return ResponseEntity.created(
            URI.create(
                "/api/flags/" + response.flagKey() + "/approval-requests/" + response.approvalId()))
        .body(response);
  }

  @PostMapping("/{approvalId}/approve")
  @Operation(summary = "Approve a feature flag update approval request")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Approval request approved",
        content = @Content(schema = @Schema(implementation = ApprovalRequestResponse.class))),
    @ApiResponse(responseCode = "403", description = "Approver role required"),
    @ApiResponse(responseCode = "404", description = "Approval request not found"),
    @ApiResponse(responseCode = "409", description = "Approval request already decided")
  })
  public ApprovalRequestResponse approve(
      @PathVariable @NotBlank @Size(max = FLAG_KEY_MAX_LENGTH) String flagKey,
      @PathVariable UUID approvalId) {
    return service.approve(flagKey, approvalId);
  }

  @PostMapping("/{approvalId}/reject")
  @Operation(summary = "Reject a feature flag update approval request")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Approval request rejected",
        content = @Content(schema = @Schema(implementation = ApprovalRequestResponse.class))),
    @ApiResponse(responseCode = "403", description = "Approver role required"),
    @ApiResponse(responseCode = "404", description = "Approval request not found"),
    @ApiResponse(responseCode = "409", description = "Approval request already decided")
  })
  public ApprovalRequestResponse reject(
      @PathVariable @NotBlank @Size(max = FLAG_KEY_MAX_LENGTH) String flagKey,
      @PathVariable UUID approvalId) {
    return service.reject(flagKey, approvalId);
  }

  @GetMapping("/{approvalId}")
  @Operation(summary = "Get a feature flag update approval request")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Approval request found",
        content = @Content(schema = @Schema(implementation = ApprovalRequestResponse.class))),
    @ApiResponse(responseCode = "403", description = "Reader is not allowed by route security"),
    @ApiResponse(responseCode = "404", description = "Approval request not found")
  })
  public ApprovalRequestResponse get(
      @PathVariable @NotBlank @Size(max = FLAG_KEY_MAX_LENGTH) String flagKey,
      @PathVariable UUID approvalId) {
    return service.get(flagKey, approvalId);
  }
}
