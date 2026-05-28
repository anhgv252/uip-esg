package com.uip.backend.bms.domain;

/**
 * Supported BMS communication protocols.
 */
public enum BmsProtocol {
    MODBUS_TCP,
    BACNET_IP,
    MQTT,
    MANUAL
}
