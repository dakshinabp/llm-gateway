package com.dakshina.llmgateway;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GatewayExceptionHandler {

    public record ApiError(int status, String message) { }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(new ApiError(ex.getStatusCode().value(), ex.getReason()));
    }

    @ExceptionHandler(DownstreamException.class)
    public ResponseEntity<ApiError> handleDownstream(DownstreamException ex) {
        return ResponseEntity
                .status(ex.status())
                .body(new ApiError(ex.status().value(), ex.getMessage()));
    }
}