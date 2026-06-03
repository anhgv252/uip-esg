package com.uip.flink.structural;

import com.uip.flink.common.NgsiLdMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link VibrationAnomalyJob} and {@link StructuralThreshold}.
 *
 * <p>Validates: structural sensor filter, boundary values (TCVN 9386:2012),
 * severity classification, and BR-010 operator review constraint.</p>
 */
@DisplayName("VibrationAnomalyJob — structural anomaly detection")
class VibrationAnomalyJobTest {

    // ─── Structural Sensor Filter ────────────────────────────────────────────

    @Test
    @DisplayName("Filter: only STRUCTURAL sensor types pass")
    void filter_onlyStructuralSensorsPass() {
        assertTrue(StructuralThreshold.isStructuralSensor("STRUCTURAL_VIBRATION"));
        assertTrue(StructuralThreshold.isStructuralSensor("STRUCTURAL_TILT"));
        assertTrue(StructuralThreshold.isStructuralSensor("STRUCTURAL_CRACK"));
        assertFalse(StructuralThreshold.isStructuralSensor("AIR_QUALITY"));
        assertFalse(StructuralThreshold.isStructuralSensor("WATER_LEVEL"));
        assertFalse(StructuralThreshold.isStructuralSensor("ENERGY"));
    }

    @Test
    @DisplayName("Filter: null message → not anomaly")
    void filter_nullMessage_returnsFalse() {
        assertFalse(VibrationAnomalyJob.isStructuralAnomaly(null));
    }

    // ─── Boundary Values — Vibration (mm/s) ─────────────────────────────────

    @ParameterizedTest(name = "Vibration {0} mm/s → anomaly={1}")
    @CsvSource({
            "9.9,  false",   // below WARNING threshold
            "10.0, true",    // exactly at WARNING
            "10.1, true",    // above WARNING
            "49.9, true",    // below CRITICAL
            "50.0, true",    // exactly at CRITICAL
            "50.1, true"     // above CRITICAL
    })
    @DisplayName("Boundary: VIBRATION threshold at TCVN 9386:2012")
    void boundary_vibration(double value, boolean expectedAnomaly) {
        NgsiLdMessage msg = createMessage("STRUCTURAL_VIBRATION", value);
        boolean result = VibrationAnomalyJob.isStructuralAnomaly(msg);
        assertEquals(expectedAnomaly, result,
                "Vibration " + value + " mm/s anomaly check failed");
    }

    // ─── Boundary Values — Tilt (mrad) ──────────────────────────────────────

    @ParameterizedTest(name = "Tilt {0} mrad → anomaly={1}")
    @CsvSource({
            "2.9,  false",
            "3.0,  true",
            "3.1,  true",
            "9.9,  true",
            "10.0, true"
    })
    @DisplayName("Boundary: TILT threshold")
    void boundary_tilt(double value, boolean expectedAnomaly) {
        NgsiLdMessage msg = createMessage("STRUCTURAL_TILT", value);
        boolean result = VibrationAnomalyJob.isStructuralAnomaly(msg);
        assertEquals(expectedAnomaly, result,
                "Tilt " + value + " mrad anomaly check failed");
    }

    // ─── Boundary Values — Crack (mm) ───────────────────────────────────────

    @ParameterizedTest(name = "Crack {0} mm → anomaly={1}")
    @CsvSource({
            "0.29, false",
            "0.3,  true",
            "0.31, true",
            "1.99, true",
            "2.0,  true"
    })
    @DisplayName("Boundary: CRACK threshold")
    void boundary_crack(double value, boolean expectedAnomaly) {
        NgsiLdMessage msg = createMessage("STRUCTURAL_CRACK", value);
        boolean result = VibrationAnomalyJob.isStructuralAnomaly(msg);
        assertEquals(expectedAnomaly, result,
                "Crack " + value + " mm anomaly check failed");
    }

    // ─── Severity Classification ────────────────────────────────────────────

    @Test
    @DisplayName("Severity: Vibration 10 → WARNING, 50 → CRITICAL")
    void severityClassification_vibration() {
        assertEquals("WARNING", StructuralThreshold.classifySeverity("STRUCTURAL_VIBRATION", 10.0));
        assertEquals("CRITICAL", StructuralThreshold.classifySeverity("STRUCTURAL_VIBRATION", 50.0));
        assertNull(StructuralThreshold.classifySeverity("STRUCTURAL_VIBRATION", 5.0));
    }

    @Test
    @DisplayName("Severity: Tilt 3 → WARNING, 10 → CRITICAL")
    void severityClassification_tilt() {
        assertEquals("WARNING", StructuralThreshold.classifySeverity("STRUCTURAL_TILT", 3.0));
        assertEquals("CRITICAL", StructuralThreshold.classifySeverity("STRUCTURAL_TILT", 10.0));
    }

    @Test
    @DisplayName("Severity: Crack 0.3 → WARNING, 2.0 → CRITICAL")
    void severityClassification_crack() {
        assertEquals("WARNING", StructuralThreshold.classifySeverity("STRUCTURAL_CRACK", 0.3));
        assertEquals("CRITICAL", StructuralThreshold.classifySeverity("STRUCTURAL_CRACK", 2.0));
    }

    @Test
    @DisplayName("Severity: unknown sensor type → null")
    void severityClassification_unknownType() {
        assertNull(StructuralThreshold.classifySeverity("UNKNOWN_SENSOR", 100.0));
    }

    // ─── Extract Measurement Value ──────────────────────────────────────────

    @Test
    @DisplayName("Extract value: empty measurements → null via default Map.of()")
    void extractValue_emptyMeasurements() {
        NgsiLdMessage msg = new NgsiLdMessage();
        // NgsiLdMessage returns Map.of() for null measurements
        assertNull(VibrationAnomalyJob.extractMeasurementValue(msg));
    }

    @Test
    @DisplayName("Extract value: numeric value extracted correctly")
    void extractValue_numericValue() {
        NgsiLdMessage msg = createMessage("STRUCTURAL_VIBRATION", 15.5);
        assertEquals(15.5, VibrationAnomalyJob.extractMeasurementValue(msg));
    }

    // ─── StructuralAlertEvent ────────────────────────────────────────────────

    @Test
    @DisplayName("StructuralAlertEvent: BR-010 requiresOperatorReview=true")
    void alertEvent_requiresOperatorReview() {
        StructuralAlertEvent event = new StructuralAlertEvent(
                "SEN-001", "STRUCTURAL_VIBRATION", "hcm",
                55.0, 5.0, 2.0, 50.0,
                "CRITICAL", "Quận 1", System.currentTimeMillis(), 3
        );
        assertTrue(event.isRequiresOperatorReview(),
                "BR-010: All structural alerts must require operator review");
        assertEquals("CRITICAL", event.getSeverity());
        assertEquals(3, event.getConsecutiveSpikes());
        assertNotNull(event.getEventId());
    }

    // ─── WelfordKeyedProcessFunction — shouldEmit logic ─────────────────────

    @Test
    @DisplayName("WelfordFilter: cold start (n<1000) → shouldEmit=false even for large spike")
    void welfordFilter_coldStart_noEmit() {
        WelfordStdDev welford = new WelfordStdDev();
        for (int i = 0; i < 999; i++) {
            welford.update(5.0);
        }
        // Still in cold start — should NOT emit regardless of value
        assertFalse(WelfordKeyedProcessFunction.shouldEmit(welford, 10000.0, 10.0),
                "Cold start (n=999): shouldEmit must be false");
    }

    @Test
    @DisplayName("WelfordFilter: after warm-up, spike above 4σ + above floor → shouldEmit=true")
    void welfordFilter_afterWarmup_spikeEmitted() {
        WelfordStdDev welford = new WelfordStdDev();
        // Pre-seed: 2000 samples, mean=5.0, variance=0.25 (stddev=0.5)
        welford.preSeed(2000, 5.0, 0.25);

        // Spike at mean + 10*sigma = 10.0 > absoluteFloor=10.0 → should emit
        assertTrue(WelfordKeyedProcessFunction.shouldEmit(welford, 10.1, 10.0),
                "Post-warmup spike above 4σ and floor: shouldEmit must be true");
    }

    @Test
    @DisplayName("WelfordFilter: normal value after warm-up → shouldEmit=false")
    void welfordFilter_afterWarmup_normalValueNotEmitted() {
        WelfordStdDev welford = new WelfordStdDev();
        welford.preSeed(2000, 5.0, 0.25);

        assertFalse(WelfordKeyedProcessFunction.shouldEmit(welford, 5.2, 10.0),
                "Normal value near mean: shouldEmit must be false");
    }

    @Test
    @DisplayName("WelfordFilter: spike above 4σ but below absoluteFloor → shouldEmit=false")
    void welfordFilter_spikeAbove4sigma_belowFloor_noEmit() {
        WelfordStdDev welford = new WelfordStdDev();
        // Very low baseline, mean≈0.1, sigma small
        for (int i = 0; i < 2000; i++) welford.update(0.1);
        // 100x mean is > 4σ, but below floor=200 → no alert
        assertFalse(WelfordKeyedProcessFunction.shouldEmit(welford, 100.0, 200.0),
                "Spike above 4σ but below regulatory floor: shouldEmit must be false");
    }

    @Test
    @DisplayName("WelfordFilter: separate sensors have independent Welford state")
    void welfordFilter_sensorIsolation() {
        // Sensor A: pre-seeded, warm — should emit
        WelfordStdDev welfordA = new WelfordStdDev();
        welfordA.preSeed(2000, 5.0, 0.25);

        // Sensor B: cold start — should NOT emit
        WelfordStdDev welfordB = new WelfordStdDev();
        for (int i = 0; i < 500; i++) welfordB.update(5.0);

        assertTrue(WelfordKeyedProcessFunction.shouldEmit(welfordA, 10.1, 10.0),
                "Sensor A (warm): should emit");
        assertFalse(WelfordKeyedProcessFunction.shouldEmit(welfordB, 10.1, 10.0),
                "Sensor B (cold): should NOT emit");
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────

    /** Create a test NgsiLdMessage using the actual field structure. */
    private NgsiLdMessage createMessage(String sensorType, double value) {
        NgsiLdMessage msg = new NgsiLdMessage();

        // deviceId → NgsiLdProperty<String>
        NgsiLdMessage.NgsiLdProperty<String> deviceId = new NgsiLdMessage.NgsiLdProperty<>();
        deviceId.setValue("SENSOR-TEST-001");
        msg.setDeviceId(deviceId);

        // observedAt → NgsiLdProperty<Long>
        NgsiLdMessage.NgsiLdProperty<Long> observedAt = new NgsiLdMessage.NgsiLdProperty<>();
        observedAt.setValue(System.currentTimeMillis());
        msg.setObservedAt(observedAt);

        // measurements → NgsiLdProperty<Map<String, Double>>
        NgsiLdMessage.NgsiLdProperty<Map<String, Double>> measurements = new NgsiLdMessage.NgsiLdProperty<>();
        measurements.setValue(Map.of("value", value));
        msg.setMeasurements(measurements);

        // meta (contains sensorType, tenantId, district)
        NgsiLdMessage.Meta meta = new NgsiLdMessage.Meta();
        meta.setSensorType(sensorType);
        meta.setTenantId("test-tenant");
        meta.setDistrict("Quận 1");
        msg.setMeta(meta);

        return msg;
    }
}
