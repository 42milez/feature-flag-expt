package com.github.milez42.featureflags.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class ErrorHandlerTest {
  private final ErrorHandler handler = new ErrorHandler();

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

  @SuppressWarnings("unchecked")
  private static ConstraintViolation<?> violation(String path, String message) {
    ConstraintViolation<?> violation = mock(ConstraintViolation.class);
    Path propertyPath = mock(Path.class);
    when(propertyPath.toString()).thenReturn(path);
    when(violation.getPropertyPath()).thenReturn(propertyPath);
    when(violation.getMessage()).thenReturn(message);
    return violation;
  }
}
