package com.uip.flink.esg;

/**
 * Thrown when a telemetry message fails validation (e.g. missing tenant_id).
 * Used as a routing signal for Flink side-output to the error topic.
 */
public class TelemetryValidationException extends Exception {

    private final String errorCode;
    private final String sensorId;

    public TelemetryValidationException(String errorCode, String sensorId, String message) {
        super(message);
        this.errorCode = errorCode;
        this.sensorId = sensorId;
    }

    public String getErrorCode() { return errorCode; }
    public String getSensorId() { return sensorId; }
}
