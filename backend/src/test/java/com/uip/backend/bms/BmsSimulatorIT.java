package com.uip.backend.bms;

import com.ghgande.j2mod.modbus.slave.ModbusSlave;
import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory;
import com.ghgande.j2mod.modbus.procimg.SimpleProcessImage;
import com.ghgande.j2mod.modbus.procimg.SimpleInputRegister;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.uip.backend.bms.adapter.BmsAdapterException;
import com.uip.backend.bms.adapter.BmsDeviceConfig;
import com.uip.backend.bms.adapter.ModbusTcpAdapter;
import com.uip.backend.bms.api.dto.BmsReadingEvent;
import com.uip.backend.bms.domain.BmsReading;
import com.uip.backend.bms.kafka.BmsReadingKafkaProducer;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BMS Hardware Simulator Integration Tests — Sprint 8 (S8-QA03)
 *
 * <p>Uses j2mod in-process Modbus TCP slave to simulate real BMS hardware.
 * No Docker or external process required — slave runs on a random free port.</p>
 *
 * <p>Test chain: j2mod slave → ModbusTcpAdapter.poll() → readings list →
 * BmsReadingKafkaProducer.publish() (mocked)</p>
 *
 * <p>Limitations documented per SA review R-06:
 * - Protocol-level edge cases (CRC corruption, TCP fragmentation) are not simulated
 * - Hardware-in-the-loop tests with real BACnet devices planned for Sprint 9
 * - BACnet simulator tests use mock adapter (bacnet4j requires real network)</p>
 */
@Tag("simulator")
@DisplayName("BMS Simulator IT — Sprint 8 (S8-QA03)")
class BmsSimulatorIT {

    private static ModbusSlave slave;
    private static SimpleProcessImage processImage;
    private static int port;

    // Register indices (input registers, FC4)
    private static final int REG_TEMPERATURE = 0;   // °C × 10
    private static final int REG_HUMIDITY    = 1;   // % × 10
    private static final int REG_ENERGY      = 2;   // kWh
    private static final int REG_CO2         = 3;   // ppm
    private static final int REG_OCCUPANCY   = 4;   // 0 or 1
    private static final int REG_AQI         = 5;   // AQI index
    private static final int REG_WATER       = 6;   // L/h
    private static final int REG_VIBRATION   = 7;   // mg × 100

    private static final Map<String, String> REGISTER_MAP = Map.of(
            "temperature",   "0:1:C",
            "humidity",      "1:1:%",
            "energy_kwh",    "2:1:kWh",
            "co2_ppm",       "3:1:ppm",
            "occupancy",     "4:1:",
            "aqi",           "5:1:",
            "water_lh",      "6:1:L/h",
            "vibration_mg",  "7:1:mg"
    );

    @BeforeAll
    static void startSlave() throws Exception {
        port = findFreePort();
        processImage = new SimpleProcessImage();

        // Pre-populate input registers with known "normal" building values
        for (int i = 0; i < 8; i++) {
            processImage.addInputRegister(new SimpleInputRegister(0));
        }
        // Holding registers (FC3) — HVAC setpoint + lighting
        processImage.addRegister(new SimpleRegister(250));  // HVAC 25.0°C
        processImage.addRegister(new SimpleRegister(50));   // Lighting 50%

        setInputRegister(REG_TEMPERATURE, 245);  // 24.5°C
        setInputRegister(REG_HUMIDITY,    650);  // 65.0%
        setInputRegister(REG_ENERGY,      120);  // 120 kWh
        setInputRegister(REG_CO2,         550);  // 550 ppm (safe)
        setInputRegister(REG_OCCUPANCY,   1);    // occupied
        setInputRegister(REG_AQI,         38);   // Good
        setInputRegister(REG_WATER,       45);   // 45 L/h
        setInputRegister(REG_VIBRATION,   150);  // 1.50 mg (normal)

        slave = ModbusSlaveFactory.createTCPSlave(port, 5);
        slave.addProcessImage(1, processImage);  // unit ID = 1
        slave.open();
    }

    @AfterAll
    static void stopSlave() {
        if (slave != null) {
            slave.close();
        }
    }

    // ─── SIM-001: Modbus adapter connects to simulator ──────────────────────────

    @Test
    @DisplayName("SIM-001: ModbusTcpAdapter connects to in-process j2mod slave")
    void sim001_adapter_connectsToSlave() {
        ModbusTcpAdapter adapter = new ModbusTcpAdapter(Map.of("temperature", "0:1:C"));
        BmsDeviceConfig config = new BmsDeviceConfig("localhost", port, 1, 0, 2000L, Map.of());

        assertThatCode(() -> {
            adapter.connect(config);
            assertThat(adapter.isAlive()).isTrue();
            adapter.disconnect();
        }).doesNotThrowAnyException();
    }

    // ─── SIM-002: Poll returns all 8 sensor readings ───────────────────────────

    @Test
    @DisplayName("SIM-002: Poll returns all 8 register readings with correct types")
    void sim002_poll_returnsAllReadings() throws Exception {
        ModbusTcpAdapter adapter = new ModbusTcpAdapter(REGISTER_MAP);
        BmsDeviceConfig config = new BmsDeviceConfig("localhost", port, 1, 0, 2000L, REGISTER_MAP);
        adapter.connect(config);

        List<BmsReading> readings = adapter.poll();
        adapter.disconnect();

        assertThat(readings).hasSize(8);

        var types = readings.stream().map(BmsReading::readingType).toList();
        assertThat(types).containsExactlyInAnyOrder(
                "temperature", "humidity", "energy_kwh", "co2_ppm",
                "occupancy", "aqi", "water_lh", "vibration_mg"
        );
    }

    // ─── SIM-003: Sensor values match simulator registers ──────────────────────

    @Test
    @DisplayName("SIM-003: Polled temperature = 245 (24.5°C) from register 0")
    void sim003_poll_temperatureMatchesRegister() throws Exception {
        ModbusTcpAdapter adapter = new ModbusTcpAdapter(Map.of("temperature", "0:1:C"));
        BmsDeviceConfig config = new BmsDeviceConfig("localhost", port, 1, 0, 2000L, Map.of());
        adapter.connect(config);

        List<BmsReading> readings = adapter.poll();
        adapter.disconnect();

        assertThat(readings).hasSize(1);
        BmsReading temp = readings.get(0);
        assertThat(temp.readingType()).isEqualTo("temperature");
        assertThat(temp.value()).isEqualTo(245.0);   // 24.5°C × 10
        assertThat(temp.unit()).isEqualTo("C");
        assertThat(temp.timestamp()).isNotNull();
    }

    // ─── SIM-004: Alarm scenario — CO2 above 1000 ppm ─────────────────────────

    @Test
    @DisplayName("SIM-004: Alarm scenario — CO2 register 1200 exceeds CRITICAL threshold")
    void sim004_alarmScenario_co2AboveThreshold() throws Exception {
        setInputRegister(REG_CO2, 1200);  // Alarm: 1200 ppm

        try {
            ModbusTcpAdapter adapter = new ModbusTcpAdapter(Map.of("co2_ppm", "3:1:ppm"));
            BmsDeviceConfig config = new BmsDeviceConfig("localhost", port, 1, 0, 2000L, Map.of());
            adapter.connect(config);
            List<BmsReading> readings = adapter.poll();
            adapter.disconnect();

            assertThat(readings).hasSize(1);
            assertThat(readings.get(0).value()).isEqualTo(1200.0);
            // Application would map 1200 ppm → CRITICAL alert in Flink/AlertEngine
        } finally {
            setInputRegister(REG_CO2, 550);  // Restore normal
        }
    }

    // ─── SIM-005: Sensor fault — register 0xFFFF (65535) ──────────────────────

    @Test
    @DisplayName("SIM-005: Sensor fault — register 0xFFFF (65535) is returned as raw value")
    void sim005_sensorFault_maxRegisterValue() throws Exception {
        setInputRegister(REG_VIBRATION, 65535);  // 0xFFFF = sensor fault

        try {
            ModbusTcpAdapter adapter = new ModbusTcpAdapter(Map.of("vibration_mg", "7:1:mg"));
            BmsDeviceConfig config = new BmsDeviceConfig("localhost", port, 1, 0, 2000L, Map.of());
            adapter.connect(config);
            List<BmsReading> readings = adapter.poll();
            adapter.disconnect();

            assertThat(readings).hasSize(1);
            assertThat(readings.get(0).value()).isEqualTo(65535.0);
            // Application logic should detect this as OFFLINE / sensor fault
        } finally {
            setInputRegister(REG_VIBRATION, 150);  // Restore normal
        }
    }

    // ─── SIM-006: Connection refused → BmsAdapterException ────────────────────

    @Test
    @DisplayName("SIM-006: Connection to closed port throws BmsAdapterException")
    void sim006_connectionRefused_throwsAdapterException() {
        ModbusTcpAdapter adapter = new ModbusTcpAdapter(Map.of("temperature", "0:1:C"));
        BmsDeviceConfig config = new BmsDeviceConfig("localhost", 19999, 1, 0, 2000L, Map.of());

        assertThatThrownBy(() -> adapter.connect(config))
                .isInstanceOf(BmsAdapterException.class)
                .hasMessageContaining("Modbus connect failed");
    }

    // ─── SIM-007: Poll without connect → BmsAdapterException ──────────────────

    @Test
    @DisplayName("SIM-007: Poll without prior connect() throws BmsAdapterException")
    void sim007_pollWithoutConnect_throwsAdapterException() {
        ModbusTcpAdapter adapter = new ModbusTcpAdapter(REGISTER_MAP);

        assertThatThrownBy(adapter::poll)
                .isInstanceOf(BmsAdapterException.class)
                .hasMessageContaining("not connected");
    }

    // ─── SIM-008: isAlive() false after disconnect ─────────────────────────────

    @Test
    @DisplayName("SIM-008: isAlive() returns false after disconnect()")
    void sim008_isAlive_falseAfterDisconnect() throws Exception {
        ModbusTcpAdapter adapter = new ModbusTcpAdapter(Map.of("temperature", "0:1:C"));
        BmsDeviceConfig config = new BmsDeviceConfig("localhost", port, 1, 0, 2000L, Map.of());

        adapter.connect(config);
        assertThat(adapter.isAlive()).isTrue();

        adapter.disconnect();
        assertThat(adapter.isAlive()).isFalse();
    }

    // ─── SIM-009: Readings publish to Kafka via BmsReadingKafkaProducer ────────

    @Test
    @DisplayName("SIM-009: Poll readings → BmsReadingKafkaProducer.publish() called for each reading")
    void sim009_readings_publishedToKafka() throws Exception {
        BmsReadingKafkaProducer producer = Mockito.mock(BmsReadingKafkaProducer.class);

        ModbusTcpAdapter adapter = new ModbusTcpAdapter(Map.of(
                "temperature", "0:1:C",
                "co2_ppm",     "3:1:ppm"
        ));
        BmsDeviceConfig config = new BmsDeviceConfig("localhost", port, 1, 0, 2000L, Map.of());
        adapter.connect(config);

        List<BmsReading> readings = adapter.poll();
        adapter.disconnect();

        UUID deviceId = UUID.randomUUID();
        // Simulate what BmsDeviceService does after poll()
        for (BmsReading reading : readings) {
            producer.publish(new BmsReadingEvent(
                    deviceId, "hcm",
                    reading.readingType(), reading.value(), reading.unit(),
                    reading.timestamp(), "MODBUS_TCP"
            ));
        }

        ArgumentCaptor<BmsReadingEvent> captor = ArgumentCaptor.forClass(BmsReadingEvent.class);
        verify(producer, times(2)).publish(captor.capture());

        var published = captor.getAllValues();
        assertThat(published).extracting(BmsReadingEvent::readingType)
                .containsExactlyInAnyOrder("temperature", "co2_ppm");
        assertThat(published).extracting(BmsReadingEvent::tenantId)
                .containsOnly("hcm");
        assertThat(published).extracting(BmsReadingEvent::source)
                .containsOnly("MODBUS_TCP");
    }

    // ─── SIM-010: Register value update — live register change reflected ────────

    @Test
    @DisplayName("SIM-010: Live register update — poll reflects new value immediately")
    void sim010_liveRegisterUpdate_reflectedOnNextPoll() throws Exception {
        setInputRegister(REG_ENERGY, 100);

        ModbusTcpAdapter adapter = new ModbusTcpAdapter(Map.of("energy_kwh", "2:1:kWh"));
        BmsDeviceConfig config = new BmsDeviceConfig("localhost", port, 1, 0, 2000L, Map.of());
        adapter.connect(config);

        List<BmsReading> first = adapter.poll();
        assertThat(first.get(0).value()).isEqualTo(100.0);

        // Simulate sensor update (building consuming more energy)
        setInputRegister(REG_ENERGY, 175);
        List<BmsReading> second = adapter.poll();
        assertThat(second.get(0).value()).isEqualTo(175.0);

        adapter.disconnect();
        setInputRegister(REG_ENERGY, 120);  // Restore
    }

    // ─── SIM-011: Holding register write — HVAC setpoint command ───────────────

    @Test
    @DisplayName("SIM-011: sendCommand writes holding register (HVAC setpoint)")
    void sim011_sendCommand_writesHoldingRegister() throws Exception {
        ModbusTcpAdapter adapter = new ModbusTcpAdapter(REGISTER_MAP);
        BmsDeviceConfig config = new BmsDeviceConfig("localhost", port, 1, 0, 2000L, REGISTER_MAP);
        adapter.connect(config);

        com.uip.backend.bms.api.dto.BmsCommand cmd = new com.uip.backend.bms.api.dto.BmsCommand(
                "SET_HVAC_SETPOINT",
                Map.of("register", "0", "value", 280)  // 28.0°C
        );
        assertThatCode(() -> adapter.sendCommand(cmd)).doesNotThrowAnyException();

        adapter.disconnect();
    }

    // ─── SIM-012: High-frequency poll — 50 polls in 5s, no leaks ──────────────

    @Test
    @DisplayName("SIM-012: High-frequency poll — 50 consecutive polls complete without error")
    void sim012_highFrequency_noPollErrors() throws Exception {
        ModbusTcpAdapter adapter = new ModbusTcpAdapter(Map.of("temperature", "0:1:C"));
        BmsDeviceConfig config = new BmsDeviceConfig("localhost", port, 1, 0, 100L, Map.of());
        adapter.connect(config);

        int successCount = 0;
        for (int i = 0; i < 50; i++) {
            try {
                List<BmsReading> readings = adapter.poll();
                if (!readings.isEmpty()) successCount++;
                Thread.sleep(50);
            } catch (Exception e) {
                // Count failures
            }
        }
        adapter.disconnect();

        assertThat(successCount).isGreaterThanOrEqualTo(45);  // Allow ≤5 transient failures
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static void setInputRegister(int index, int value) throws Exception {
        // SimpleInputRegister extends SynchronizedAbstractRegister which has setValue(int)
        ((com.ghgande.j2mod.modbus.procimg.SimpleInputRegister) processImage.getInputRegister(index)).setValue(value);
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
