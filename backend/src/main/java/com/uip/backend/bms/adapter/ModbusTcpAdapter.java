package com.uip.backend.bms.adapter;

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.uip.backend.bms.api.dto.BmsCommand;
import com.uip.backend.bms.domain.BmsProtocol;
import com.uip.backend.bms.domain.BmsReading;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class ModbusTcpAdapter implements BmsProtocolAdapter {

    private static final int DEFAULT_TIMEOUT_MS = 3000;
    private static final int DEFAULT_RETRIES = 3;

    private final Map<String, String> registerMap;
    private ModbusTCPMaster master;
    private BmsDeviceConfig config;
    private volatile boolean connected;

    public ModbusTcpAdapter(Map<String, String> registerMap) {
        this.registerMap = registerMap != null ? registerMap : Map.of();
    }

    @Override
    public String getProtocol() {
        return BmsProtocol.MODBUS_TCP.name();
    }

    @Override
    public void connect(BmsDeviceConfig config) {
        this.config = config;
        try {
            master = new ModbusTCPMaster(config.host(), config.port());
            master.setTimeout(DEFAULT_TIMEOUT_MS);
            master.setRetries(DEFAULT_RETRIES);
            master.connect();
            connected = true;
            log.info("Modbus connected: {}:{}", config.host(), config.port());
        } catch (Exception e) {
            connected = false;
            throw new BmsAdapterException("Modbus connect failed: " + config.host() + ":" + config.port(), e);
        }
    }

    @Override
    public void disconnect() {
        if (master != null) {
            try {
                master.disconnect();
            } catch (Exception e) {
                log.warn("Modbus disconnect error: {}", e.getMessage());
            }
            connected = false;
        }
    }

    @Override
    public List<BmsReading> poll() {
        if (!connected || master == null) {
            throw new BmsAdapterException("Modbus not connected");
        }

        List<BmsReading> readings = new ArrayList<>();
        for (Map.Entry<String, String> entry : registerMap.entrySet()) {
            String readingType = entry.getKey();
            String[] parts = entry.getValue().split(":");
            int registerAddress = Integer.parseInt(parts[0]);
            int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            String unit = parts.length > 2 ? parts[2] : "";

            try {
                InputRegister[] registers = master.readInputRegisters(config.unitId(), registerAddress, count);
                if (registers != null && registers.length > 0) {
                    double value = registers[0].getValue();
                    readings.add(new BmsReading(readingType, value, unit, Instant.now()));
                }
            } catch (Exception e) {
                log.warn("Modbus read failed: register={} type={}: {}", registerAddress, readingType, e.getMessage());
                throw new BmsAdapterException("Modbus read failed: " + readingType, e);
            }
        }
        return readings;
    }

    @Override
    public boolean isAlive() {
        return connected && master != null;
    }

    @Override
    public void sendCommand(BmsCommand command) {
        if (!connected || master == null) {
            throw new BmsAdapterException("Modbus not connected");
        }
        try {
            Object value = command.payload().get("value");
            int registerAddress = Integer.parseInt(command.payload().getOrDefault("register", "0").toString());
            int intValue = value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
            master.writeSingleRegister(config.unitId(), registerAddress, new SimpleRegister(intValue));
            log.info("Modbus command sent: register={} value={}", registerAddress, intValue);
        } catch (Exception e) {
            throw new BmsAdapterException("Modbus command failed: " + command.commandType(), e);
        }
    }
}
