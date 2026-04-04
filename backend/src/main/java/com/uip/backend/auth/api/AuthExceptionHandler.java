package com.uip.backend.auth.api;

import com.uip.backend.auth.exception.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Exception handler cho auth-module.
 * Đặt trong auth package để auth tự xử lý exception của chính nó —
 * không để GlobalExceptionHandler (common) phụ thuộc vào auth.
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        detail.setType(URI.create("/errors/invalid-credentials"));
        return detail;
    }
}
