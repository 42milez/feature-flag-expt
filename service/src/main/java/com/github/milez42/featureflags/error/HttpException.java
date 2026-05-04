package com.github.milez42.featureflags.error;

import org.springframework.http.HttpStatus;

public abstract class HttpException extends RuntimeException {
    protected HttpException(String message) {
        super(message);
    }

    public abstract HttpStatus status();
    public abstract String title();
}
