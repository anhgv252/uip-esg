package com.uip.backend.forecast;

public class ForecastServiceUnavailableException extends RuntimeException {
    public ForecastServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
