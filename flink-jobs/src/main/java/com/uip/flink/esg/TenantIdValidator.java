package com.uip.flink.esg;

import com.uip.flink.common.NgsiLdMessage;
import com.uip.flink.common.tenant.TenantKeyedProcessFunctionDelegate;
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
 * ADR-014 / ADR-047 §1.3 — TenantIdValidator.
 *
 * <p>Validates tenant_id presence in sensor messages. Messages with a
 * null/blank tenant_id are routed to the {@link #ERROR_TAG} side output for
 * observability (downstream DLQ). Valid messages are passed to the main output
 * <em>and</em> the tenant is bound to {@link com.uip.flink.common.tenant.TenantContext}
 * for the duration of {@code processElement} via the tenant delegate (fail-closed).</p>
 *
 * <p>Registered as a proper Flink operator so {@code open()} is invoked and
 * metrics are wired.</p>
 */
public class TenantIdValidator extends ProcessFunction<NgsiLdMessage, NgsiLdMessage> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(TenantIdValidator.class);

    public static final OutputTag<Map<String, Object>> ERROR_TAG =
            new OutputTag<Map<String, Object>>("telemetry-errors") {};

    private transient Counter validCount;
    private transient Counter missingCount;

    /**
     * Tenant guard (ADR-047 §1.3). Records that reach the delegate always carry a
     * non-blank tenant (we route missing-tenant records to the side output BEFORE
     * delegating), so the guard's fail-closed drop path is not exercised in normal
     * flow — it is a defense-in-depth against a future caller that forgets the
     * pre-check.
     *
     * <p>Initialised in {@code open()} (not as a field initializer) because Flink
     * distributes operators by serialization; {@code transient} fields are not restored
     * and field initializers do not re-run on the task manager. {@code open()} is the
     * single reliable place to construct per-operator state.</p>
     */
    private transient TenantKeyedProcessFunctionDelegate<NgsiLdMessage, NgsiLdMessage> guard;

    @Override
    public void open(Configuration parameters) {
        guard = TenantKeyedProcessFunctionDelegate.forFn(NgsiLdMessage::getTenantId);
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
        // Valid record — bind TenantContext then collect (fail-closed inside the delegate).
        guard.run(msg, (rec, emit) -> {
            validCount.inc();
            emit.accept(rec);
        }, out::collect);
    }
}
