package com.github.milez42.featureflags.error;

import com.github.milez42.featureflags.policy.RolloutPolicyValidationResponse;
import com.github.milez42.featureflags.policy.RolloutPolicyValidationResult;
import com.github.milez42.featureflags.policy.RolloutPolicyViolationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorHandler {
  @ExceptionHandler(RolloutPolicyViolationException.class)
  public ResponseEntity<RolloutPolicyValidationResponse> handle(RolloutPolicyViolationException e) {
    return ResponseEntity.unprocessableContent().body(toResponse(e.result()));
  }

  @ExceptionHandler(HttpException.class)
  public ResponseEntity<ProblemDetail> handle(HttpException e) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    problem.setTitle(e.title());
    return ResponseEntity.status(e.status()).body(problem);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handle(ConstraintViolationException e) {
    String detail =
        e.getConstraintViolations().stream()
            .sorted(
                Comparator.comparing(
                        (ConstraintViolation<?> violation) ->
                            violation.getPropertyPath().toString())
                    .thenComparing(ConstraintViolation::getMessage))
            .map(ErrorHandler::formatViolation)
            .collect(Collectors.joining(", "));
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    problem.setTitle("Invalid request");
    return ResponseEntity.badRequest().body(problem);
  }

  private static String formatViolation(ConstraintViolation<?> violation) {
    String path = violation.getPropertyPath().toString();
    if (path.isBlank()) {
      return violation.getMessage();
    }
    return path + ": " + violation.getMessage();
  }

  private static RolloutPolicyValidationResponse toResponse(RolloutPolicyValidationResult result) {
    return new RolloutPolicyValidationResponse(
        result.flagKey(), result.allowed(), result.violations());
  }
}
