package com.uip.backend.bms.api.dto;

import java.util.Map;

/**
 * Command to send to a BMS device via MQTT.
 */
public record BmsCommand(
        String commandType,
        Map<String, Object> payload
) {}
