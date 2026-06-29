package com.uip.backend.billing.domain;

/**
 * Types of metering events for tenant billing.
 * Each type maps to a different cost model.
 * 
 * M5-3 T06: Added SENSOR_READING, ALERT_GENERATED, BPMN_WORKFLOW_EXECUTED for ROI tracking.
 */
public enum MeteringEventType {
    AI_PREDICTION,              // AI model prediction (e.g., flood risk scoring)
    AI_TRAINING,                // AI model training job
    AI_INFERENCE,               // AI inference execution (NL→BPMN, Claude API calls)
    WORKFLOW_RUN,               // BPMN workflow execution
    API_CALL,                   // External API call (e.g., OpenAI, Azure OpenAI)
    SENSOR_READING,             // IoT sensor reading ingested
    ALERT_GENERATED,            // Alert triggered by Alert Engine
    BPMN_WORKFLOW_EXECUTED      // BPMN workflow execution (automated or manual trigger)
}
