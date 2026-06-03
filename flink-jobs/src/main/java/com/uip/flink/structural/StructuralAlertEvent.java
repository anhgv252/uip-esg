package com.uip.flink.structural;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Kafka event emitted when a structural anomaly is detected.
 *
 * <p>Published to topic {@code UIP.structural.alert.critical.v1} by
 * {@link VibrationAnomalyJob} and consumed by the monolith's
 * {@code StructuralAlertConsumer}.</p>
 *
 * <p><strong>BR-010:</strong> P0 (CRITICAL) alerts require operator review.
 * The system does NOT auto-evacuate.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StructuralAlertEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("sensorId")
    private String sensorId;

    @JsonProperty("sensorType")
    private String sensorType;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("buildingId")
    private String buildingId;

    @JsonProperty("measuredValue")
    private double measuredValue;

    @JsonProperty("meanValue")
    private double meanValue;

    @JsonProperty("stdDevValue")
    private double stdDevValue;

    @JsonProperty("thresholdValue")
    private double thresholdValue;

    @JsonProperty("severity")
    private String severity;

    @JsonProperty("district")
    private String district;

    @JsonProperty("observedAtMillis")
    private long observedAtMillis;

    @JsonProperty("consecutiveSpikes")
    private int consecutiveSpikes;

    @JsonProperty("requiresOperatorReview")
    private boolean requiresOperatorReview;

    public StructuralAlertEvent() {}

    public StructuralAlertEvent(String sensorId, String sensorType, String tenantId,
                                 double measuredValue, double meanValue, double stdDevValue,
                                 double thresholdValue, String severity, String district,
                                 long observedAtMillis, int consecutiveSpikes) {
        this.eventId = UUID.randomUUID().toString();
        this.sensorId = sensorId;
        this.sensorType = sensorType;
        this.tenantId = tenantId;
        this.measuredValue = measuredValue;
        this.meanValue = meanValue;
        this.stdDevValue = stdDevValue;
        this.thresholdValue = thresholdValue;
        this.severity = severity;
        this.district = district;
        this.observedAtMillis = observedAtMillis;
        this.consecutiveSpikes = consecutiveSpikes;
        // BR-010: All structural alerts require operator review
        this.requiresOperatorReview = true;
    }

    // Getters
    public String getEventId() { return eventId; }
    public String getSensorId() { return sensorId; }
    public String getSensorType() { return sensorType; }
    public String getTenantId() { return tenantId; }
    public String getBuildingId() { return buildingId; }
    public double getMeasuredValue() { return measuredValue; }
    public double getMeanValue() { return meanValue; }
    public double getStdDevValue() { return stdDevValue; }
    public double getThresholdValue() { return thresholdValue; }
    public String getSeverity() { return severity; }
    public String getDistrict() { return district; }
    public long getObservedAtMillis() { return observedAtMillis; }
    public int getConsecutiveSpikes() { return consecutiveSpikes; }
    public boolean isRequiresOperatorReview() { return requiresOperatorReview; }

    // Setters
    public void setBuildingId(String buildingId) { this.buildingId = buildingId; }

    @Override
    public String toString() {
        return "StructuralAlertEvent{" +
                "eventId='" + eventId + '\'' +
                ", sensorId='" + sensorId + '\'' +
                ", sensorType='" + sensorType + '\'' +
                ", severity='" + severity + '\'' +
                ", measuredValue=" + measuredValue +
                ", tenantId='" + tenantId + '\'' +
                ", consecutiveSpikes=" + consecutiveSpikes +
                '}';
    }
}
