package com.uip.backend.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TraceIdFilter — covers 4 branches:
 *  1. Request has valid trace ID header → use provided ID
 *  2. Request has null trace ID header → generate UUID
 *  3. Request has blank trace ID header → generate UUID
 *  4. MDC is always cleaned up after filter (try-finally)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TraceIdFilter — unit")
class TraceIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private TraceIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter();
        MDC.clear(); // ensure clean MDC before each test
    }

    @Test
    @DisplayName("doFilterInternal — uses provided X-Trace-Id when header present")
    void doFilter_withTraceIdHeader_usesProvidedId() throws Exception {
        String traceId = "abc123def456";
        when(request.getHeader(TraceIdFilter.TRACE_ID_HEADER)).thenReturn(traceId);

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<String> responseHeaderCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq(TraceIdFilter.TRACE_ID_HEADER), responseHeaderCaptor.capture());
        assertThat(responseHeaderCaptor.getValue()).isEqualTo(traceId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilterInternal — generates UUID when X-Trace-Id header is null")
    void doFilter_noTraceIdHeader_generatesUuid() throws Exception {
        when(request.getHeader(TraceIdFilter.TRACE_ID_HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<String> responseHeaderCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq(TraceIdFilter.TRACE_ID_HEADER), responseHeaderCaptor.capture());
        String generatedId = responseHeaderCaptor.getValue();
        assertThat(generatedId).isNotBlank();
        assertThat(generatedId).hasSize(16); // UUID without dashes, first 16 chars
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilterInternal — generates UUID when X-Trace-Id header is blank")
    void doFilter_blankTraceIdHeader_generatesUuid() throws Exception {
        when(request.getHeader(TraceIdFilter.TRACE_ID_HEADER)).thenReturn("   ");

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<String> responseHeaderCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq(TraceIdFilter.TRACE_ID_HEADER), responseHeaderCaptor.capture());
        String generatedId = responseHeaderCaptor.getValue();
        assertThat(generatedId).isNotBlank();
        assertThat(generatedId).hasSize(16);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilterInternal — MDC is cleared after filter chain completes")
    void doFilter_clearsMdcAfterChain() throws Exception {
        when(request.getHeader(TraceIdFilter.TRACE_ID_HEADER)).thenReturn("trace-001");

        filter.doFilterInternal(request, response, filterChain);

        // MDC should be cleaned up after filter
        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("doFilterInternal — MDC is cleared even when filter chain throws exception")
    void doFilter_clearsMdcWhenChainThrows() throws Exception {
        when(request.getHeader(TraceIdFilter.TRACE_ID_HEADER)).thenReturn("trace-002");
        doThrow(new RuntimeException("chain error")).when(filterChain).doFilter(any(), any());

        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("doFilterInternal — generated IDs are unique across calls")
    void doFilter_generatedIds_areUnique() throws Exception {
        when(request.getHeader(TraceIdFilter.TRACE_ID_HEADER)).thenReturn(null);

        ArgumentCaptor<String> captor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor2 = ArgumentCaptor.forClass(String.class);

        HttpServletResponse response2 = mock(HttpServletResponse.class);

        filter.doFilterInternal(request, response, filterChain);
        verify(response).setHeader(eq(TraceIdFilter.TRACE_ID_HEADER), captor1.capture());

        filter.doFilterInternal(request, response2, filterChain);
        verify(response2).setHeader(eq(TraceIdFilter.TRACE_ID_HEADER), captor2.capture());

        assertThat(captor1.getValue()).isNotEqualTo(captor2.getValue());
    }
}
