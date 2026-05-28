package com.uip.backend.bms.adapter;

public class BmsAdapterException extends RuntimeException {
    public BmsAdapterException(String message) {
        super(message);
    }

    public BmsAdapterException(String message, Throwable cause) {
        super(message, cause);
    }
}
