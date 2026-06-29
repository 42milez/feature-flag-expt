package com.github.milez42.featureflags.flags;

import static com.github.milez42.featureflags.flags.FeatureFlagConstraints.FLAG_KEY_MAX_LENGTH;
import static com.github.milez42.featureflags.flags.FeatureFlagConstraints.FLAG_KEY_MIN_LENGTH;

import com.github.milez42.featureflags.audit.AuditEventResponse;
import com.github.milez42.featureflags.policy.RolloutPolicyValidationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Feature Flags", description = "Feature flag management, evaluation, and audit APIs.")
@Validated
public class FeatureFlagController {
  private final FeatureFlagService service;

  public FeatureFlagController(FeatureFlagService service) {
    this.service = service;
  }

  @PostMapping("/flags")
  @Operation(summary = "Create a feature flag")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Feature flag created",
        content = @Content(schema = @Schema(implementation = FeatureFlagResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Feature flag already exists",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description = "Rollout policy violation",
        content =
            @Content(schema = @Schema(implementation = RolloutPolicyValidationResponse.class)))
  })
  public ResponseEntity<FeatureFlagResponse> create(
      @Valid @RequestBody CreateFeatureFlagRequest request) {
    FeatureFlagResponse response = service.create(request);
    return ResponseEntity.created(URI.create("/api/flags/" + response.flagKey())).body(response);
  }

  @GetMapping("/flags/{flagKey}")
  @Operation(summary = "Get a feature flag")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Feature flag found",
        content = @Content(schema = @Schema(implementation = FeatureFlagResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Feature flag not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public FeatureFlagResponse get(
      @Parameter(
              description = "Feature flag key.",
              example = "checkout-redesign",
              schema = @Schema(minLength = FLAG_KEY_MIN_LENGTH, maxLength = FLAG_KEY_MAX_LENGTH))
          @PathVariable
          @NotBlank
          @Size(max = FLAG_KEY_MAX_LENGTH)
          String flagKey) {
    return service.get(flagKey);
  }

  @GetMapping("/flags/{flagKey}/audit-events")
  @Operation(summary = "List audit events for a feature flag")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Audit events ordered oldest first",
        content =
            @Content(
                array = @ArraySchema(schema = @Schema(implementation = AuditEventResponse.class)))),
    @ApiResponse(
        responseCode = "404",
        description = "Feature flag not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public List<AuditEventResponse> auditEvents(
      @Parameter(
              description = "Feature flag key.",
              example = "checkout-redesign",
              schema = @Schema(minLength = FLAG_KEY_MIN_LENGTH, maxLength = FLAG_KEY_MAX_LENGTH))
          @PathVariable
          @NotBlank
          @Size(max = FLAG_KEY_MAX_LENGTH)
          String flagKey) {
    return service.auditEvents(flagKey);
  }

  @PatchMapping("/flags/{flagKey}")
  @Operation(summary = "Update a feature flag")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Feature flag updated",
        content = @Content(schema = @Schema(implementation = FeatureFlagResponse.class))),
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
  public FeatureFlagResponse update(
      @Parameter(
              description = "Feature flag key.",
              example = "checkout-redesign",
              schema = @Schema(minLength = FLAG_KEY_MIN_LENGTH, maxLength = FLAG_KEY_MAX_LENGTH))
          @PathVariable
          @NotBlank
          @Size(max = FLAG_KEY_MAX_LENGTH)
          String flagKey,
      @Valid @RequestBody UpdateFeatureFlagRequest request) {
    return service.update(flagKey, request);
  }

  @PostMapping("/evaluate")
  @Operation(summary = "Evaluate a feature flag")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Evaluation result",
        content = @Content(schema = @Schema(implementation = EvaluateFeatureFlagResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Feature flag not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public EvaluateFeatureFlagResponse evaluate(
      @Valid @RequestBody EvaluateFeatureFlagRequest request) {
    return service.evaluate(request);
  }
}
