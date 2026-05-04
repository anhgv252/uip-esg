package com.uip.flink.esg;

import com.uip.flink.common.NgsiLdMessage;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ADR-014 — TenantIdValidator
 * Validates tenant_id presence in sensor messages.
 * Missing tenant_id → thrown as TelemetryValidationException for side-output routing.
 */
public class TenantIdValidator extends RichMapFunction<NgsiLdMessage, NgsiLdMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(TenantIdValidator.class);

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
    public NgsiLdMessage map(NgsiLdMessage msg) throws TelemetryValidationException {
        String tenantId = msg.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            missingCount.inc();
            LOG.warn("Missing tenant_id for deviceId={} — routing to error topic",
                    msg.getDeviceIdValue());
            throw new TelemetryValidationException(
                    "MISSING_TENANT_ID",
                    msg.getDeviceIdValue(),
                    "tenant_id is required but was null or blank");
        }
        validCount.inc();
        return msg;
    }
}
