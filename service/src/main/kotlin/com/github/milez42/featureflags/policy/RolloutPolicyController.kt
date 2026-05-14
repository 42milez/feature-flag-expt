package com.github.milez42.featureflags.policy

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ProblemDetail
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
@Tag(name = "Rollout Policy", description = "Validate proposed feature flag changes.")
@Validated
class RolloutPolicyController(private val validator: RolloutPolicyValidator) {
  @PostMapping("/flags/{flagKey}/validate-change")
  @Operation(
      summary = "Validate a feature flag change",
      description =
          "Validates the feature flag end state after applying proposedChange to the current flag. " +
              "This endpoint does not validate only the request delta, so it may also return " +
              "pre-existing violations from the current flag state whenever they still exist in " +
              "the proposed end state.",
  )
  @ApiResponses(
      value =
          [
              ApiResponse(
                  responseCode = "200",
                  description = "Policy validation result",
                  content =
                      [
                          Content(
                              schema = Schema(implementation = RolloutPolicyValidationResult::class)
                          )
                      ],
              ),
              ApiResponse(
                  responseCode = "400",
                  description = "Invalid request",
                  content = [Content(schema = Schema(implementation = ProblemDetail::class))],
              ),
              ApiResponse(
                  responseCode = "404",
                  description = "Feature flag not found",
                  content = [Content(schema = Schema(implementation = ProblemDetail::class))],
              ),
          ]
  )
  fun validateChange(
      @Parameter(
          description = "Feature flag key.",
          example = "checkout-redesign",
          schema = Schema(minLength = 1, maxLength = 200),
      )
      @PathVariable
      @NotBlank
      @Size(max = 200)
      flagKey: String,
      @Valid @RequestBody request: RolloutPolicyValidationRequest,
  ): RolloutPolicyValidationResult = validator.validate(flagKey, request)
}
