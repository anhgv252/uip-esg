package com.uip.flink.esg;

import com.uip.flink.common.NgsiLdMessage;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * ADR-014 — TenantIdValidator
 * Validates tenant_id presence in sensor messages.
 * Valid messages pass to main output; missing tenant_id → error side output.
 * Registered as a proper Flink operator so open() is invoked and metrics are wired.
 */
public class TenantIdValidator extends ProcessFunction<NgsiLdMessage, NgsiLdMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(TenantIdValidator.class);

    public static final OutputTag<Map<String, Object>> ERROR_TAG =
            new OutputTag<Map<String, Object>>("telemetry-errors") {};

    private transient Counter validCount;
    private transient Counter missingCount;

    @Override
    public void open(Configuration parameters) {
        validCount = getRuntimeContext()
                .getMetricGroup()
                .addGroup("uip", "esg")
                .counter("tenant_id_valid_count");
        missingCount = getRuntimeContext()
                .getMetricGroup()
                .addGroup("uip", "esg")
                .counter("tenant_id_missing_count");
    }

    @Override
    public void processElement(NgsiLdMessage msg, Context ctx, Collector<NgsiLdMessage> out) {
        String tenantId = msg.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            missingCount.inc();
            LOG.warn("Missing tenant_id for deviceId={} — routing to error topic",
                    msg.getDeviceIdValue());
            Map<String, Object> error = new HashMap<>();
            error.put("errorCode", "MISSING_TENANT_ID");
            error.put("sensorId", msg.getDeviceIdValue() != null ? msg.getDeviceIdValue() : "unknown");
            error.put("message", "tenant_id is required but was null or blank");
            error.put("detectedAt", Instant.now().toString());
            ctx.output(ERROR_TAG, error);
            return;
        }
        validCount.inc();
        out.collect(msg);
    }
}
