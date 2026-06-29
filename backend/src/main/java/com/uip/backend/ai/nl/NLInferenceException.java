package com.uip.backend.ai.nl;

/**
 * Exception thrown when NL inference fails (timeout, model down, 503).
 * 
 * <p>Distinct from ModelRouterException (routing logic failure).
 */
public class NLInferenceException extends RuntimeException {
    private final boolean retryable;
    
    public NLInferenceException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }
    
    public NLInferenceException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
}
