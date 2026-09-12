package com.dakshina.llmgateway;

import org.springframework.http.HttpStatus;

public class DownstreamException extends RuntimeException {

    private final HttpStatus status;
    private final boolean retryable;

    public DownstreamException(HttpStatus status, String message, boolean retryable) {
        super(message);
        this.status = status;
        this.retryable = retryable;
    }

    public HttpStatus status() {
        return status;
    }

    public boolean retryable() {
        return retryable;
    }
}