package com.github.milez42.featureflags.error;

import com.github.milez42.featureflags.flags.FeatureFlagDuplicateException;
import com.github.milez42.featureflags.flags.FeatureFlagNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorHandler {
    @ExceptionHandler(FeatureFlagNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(FeatureFlagNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Feature flag not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(FeatureFlagDuplicateException.class)
    public ResponseEntity<ProblemDetail> handleDuplicate(FeatureFlagDuplicateException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Feature flag conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
