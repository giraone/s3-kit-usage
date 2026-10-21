package com.giraone.s3kit.demo.web;

import com.giraone.s3kit.exceptions.S3CallFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(S3CallFailedException.class)
    public ResponseEntity<ProblemDetail> handleS3CallFailed(final S3CallFailedException e) {
        final int code = e.getHttpStatusCode();
        final HttpStatus status = code == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, e.getMessage()));
    }
}
