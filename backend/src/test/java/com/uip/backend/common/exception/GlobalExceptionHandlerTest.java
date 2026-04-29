package com.uip.backend.common.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * MVP2-03c (3d): Unit tests for GlobalExceptionHandler.
 * Verifies correct HTTP status codes, ProblemDetail fields (traceId, timestamp, path),
 * and error type URIs for each exception type.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MVP2-03c GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/v1/test/resource/123");
    }

    // --- EntityNotFoundException -> 404 -------------------------------------------

    @Test
    @DisplayName("EntityNotFoundException -> 404 NOT_FOUND with message, path, timestamp")
    void entityNotFoundException_returns404() {
        EntityNotFoundException ex = new EntityNotFoundException("Alert not found: abc-123");

        ProblemDetail detail = handler.handleNotFound(ex, request);

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(detail.getDetail()).isEqualTo("Alert not found: abc-123");
        assertThat(detail.getType().toString()).isEqualTo("/errors/not-found");
        assertThat(detail.getProperties()).containsKey("timestamp");
        assertThat(detail.getProperties()).containsEntry("path", "/api/v1/test/resource/123");
    }

    // --- IllegalStateException -> 503 ---------------------------------------------

    @Test
    @DisplayName("IllegalStateException -> 503 SERVICE_UNAVAILABLE with message")
    void illegalStateException_returns503() {
        IllegalStateException ex = new IllegalStateException("External service unavailable");

        ProblemDetail detail = handler.handleIllegalState(ex, request);

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(detail.getDetail()).isEqualTo("External service unavailable");
        assertThat(detail.getType().toString()).isEqualTo("/errors/service-unavailable");
        assertThat(detail.getProperties()).containsKey("timestamp");
        assertThat(detail.getProperties()).containsEntry("path", "/api/v1/test/resource/123");
    }

    // --- Generic Exception -> 500 -------------------------------------------------

    @Test
    @DisplayName("Generic Exception -> 500 INTERNAL_SERVER_ERROR")
    void genericException_returns500() {
        Exception ex = new RuntimeException("Unexpected null pointer");

        ProblemDetail detail = handler.handleGeneral(ex, request);

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(detail.getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(detail.getType().toString()).isEqualTo("/errors/internal");
        assertThat(detail.getProperties()).containsKey("timestamp");
        assertThat(detail.getProperties()).containsEntry("path", "/api/v1/test/resource/123");
    }

    // --- AccessDeniedException -> 403 ---------------------------------------------

    @Test
    @DisplayName("AccessDeniedException -> 403 FORBIDDEN")
    void accessDeniedException_returns403() {
        AccessDeniedException ex = new AccessDeniedException("Insufficient permissions");

        ProblemDetail detail = handler.handleAccessDenied(ex, request);

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(detail.getDetail()).isEqualTo("Access denied");
        assertThat(detail.getType().toString()).isEqualTo("/errors/access-denied");
        assertThat(detail.getProperties()).containsKey("timestamp");
        assertThat(detail.getProperties()).containsEntry("path", "/api/v1/test/resource/123");
    }

    // --- WorkflowNotFoundException -> 404 -----------------------------------------

    @Test
    @DisplayName("WorkflowNotFoundException -> 404 NOT_FOUND")
    void workflowNotFoundException_returns404() {
        WorkflowNotFoundException ex = new WorkflowNotFoundException("process-123");

        ProblemDetail detail = handler.handleWorkflowNotFound(ex, request);

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(detail.getType().toString()).isEqualTo("/errors/workflow-not-found");
        assertThat(detail.getProperties()).containsKey("timestamp");
        assertThat(detail.getProperties()).containsEntry("path", "/api/v1/test/resource/123");
    }

    // --- TraceId propagation ------------------------------------------------------

    @Test
    @DisplayName("ProblemDetail includes traceId when MDC is set")
    void problemDetail_includesTraceId_whenMdcSet() {
        org.slf4j.MDC.put("traceId", "trace-abc-123");

        try {
            EntityNotFoundException ex = new EntityNotFoundException("Not found");
            ProblemDetail detail = handler.handleNotFound(ex, request);

            assertThat(detail.getProperties()).containsEntry("traceId", "trace-abc-123");
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
    }

    @Test
    @DisplayName("ProblemDetail has no traceId when MDC is empty")
    void problemDetail_noTraceId_whenMdcEmpty() {
        org.slf4j.MDC.clear();

        EntityNotFoundException ex = new EntityNotFoundException("Not found");
        ProblemDetail detail = handler.handleNotFound(ex, request);

        assertThat(detail.getProperties()).doesNotContainKey("traceId");
    }

    // --- Timestamp present on all responses ---------------------------------------

    @Test
    @DisplayName("All exception handlers include timestamp in response")
    void allHandlers_includeTimestamp() {
        ProblemDetail notFound = handler.handleNotFound(
                new EntityNotFoundException("x"), request);
        ProblemDetail illegalState = handler.handleIllegalState(
                new IllegalStateException("x"), request);
        ProblemDetail generic = handler.handleGeneral(
                new RuntimeException("x"), request);
        ProblemDetail accessDenied = handler.handleAccessDenied(
                new AccessDeniedException("x"), request);

        assertThat(notFound.getProperties()).containsKey("timestamp");
        assertThat(illegalState.getProperties()).containsKey("timestamp");
        assertThat(generic.getProperties()).containsKey("timestamp");
        assertThat(accessDenied.getProperties()).containsKey("timestamp");
    }
}
