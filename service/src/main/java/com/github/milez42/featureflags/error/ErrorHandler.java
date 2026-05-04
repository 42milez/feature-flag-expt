package com.github.milez42.featureflags.error;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorHandler {
    @ExceptionHandler(HttpException.class)
    public ResponseEntity<ProblemDetail> handle(HttpException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        problem.setTitle(e.title());
        return ResponseEntity.status(e.status()).body(problem);
    }
}
