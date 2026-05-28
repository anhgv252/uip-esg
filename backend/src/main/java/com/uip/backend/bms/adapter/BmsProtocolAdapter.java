package com.uip.backend.bms.adapter;

import com.uip.backend.bms.api.dto.BmsCommand;
import com.uip.backend.bms.domain.BmsReading;

import java.util.List;

/**
 * Strategy interface for BMS protocol adapters (ADR-029).
 *
 * Each protocol (Modbus TCP, BACnet/IP) implements this interface.
 * Lifecycle: connect → poll (scheduled) → disconnect.
 */
public interface BmsProtocolAdapter {

    String getProtocol();

    void connect(BmsDeviceConfig config);

    void disconnect();

    List<BmsReading> poll();

    boolean isAlive();

    void sendCommand(BmsCommand command);
}
