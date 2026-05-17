package com.github.milez42.featureflags.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.milez42.featureflags.policy.RolloutPolicyValidationResponse;
import com.github.milez42.featureflags.policy.RolloutPolicyValidationResult;
import com.github.milez42.featureflags.policy.RolloutPolicyViolation;
import com.github.milez42.featureflags.policy.RolloutPolicyViolationException;
import com.github.milez42.featureflags.policy.Severity;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class ErrorHandlerTest {
  private final ErrorHandler handler = new ErrorHandler();

  @Test
  void rolloutPolicyViolationReturnsKotlinResponseDto() {
    RolloutPolicyViolation violation =
        new RolloutPolicyViolation("FULL_PRODUCTION_ROLLOUT", "Requires approval", Severity.ERROR);
    RolloutPolicyValidationResult result =
        RolloutPolicyValidationResult.from("checkout-redesign", List.of(violation));

    RolloutPolicyValidationResponse response =
        handler.handle(new RolloutPolicyViolationException(result)).getBody();

    assertThat(response).isNotNull();
    assertThat(response.getFlagKey()).isEqualTo("checkout-redesign");
    assertThat(response.getAllowed()).isFalse();
    assertThat(response.getViolations()).containsExactly(violation);
  }

  @Test
  void constraintViolationDetailsAreSortedByPathThenMessage() {
    Set<ConstraintViolation<?>> violations = new LinkedHashSet<>();
    violations.add(violation("sampleContexts", "must not be empty"));
    violations.add(violation("flagKey", "size must be between 0 and 200"));
    violations.add(violation("flagKey", "must not be blank"));
    ConstraintViolationException exception = new ConstraintViolationException(violations);

    ProblemDetail problem = handler.handle(exception).getBody();

    assertThat(problem).isNotNull();
    assertThat(problem.getDetail())
        .isEqualTo(
            "flagKey: must not be blank, flagKey: size must be between 0 and 200, sampleContexts: must not be empty");
  }

  private static ConstraintViolation<?> violation(String path, String message) {
    ConstraintViolation<?> violation = mock(ConstraintViolation.class);
    Path propertyPath = mock(Path.class);
    when(propertyPath.toString()).thenReturn(path);
    when(violation.getPropertyPath()).thenReturn(propertyPath);
    when(violation.getMessage()).thenReturn(message);
    return violation;
  }
}
