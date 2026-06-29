package com.uip.backend.ai.nl;

/**
 * Exception thrown when ModelRouter itself fails (config error, invalid state).
 * 
 * <p>Distinct from inference failures (timeout, model down) which return 503.
 */
public class ModelRouterException extends RuntimeException {
    public ModelRouterException(String message) {
        super(message);
    }
    
    public ModelRouterException(String message, Throwable cause) {
        super(message, cause);
    }
}
