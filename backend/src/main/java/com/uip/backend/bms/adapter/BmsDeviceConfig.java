package com.uip.backend.bms.adapter;

/**
 * Configuration for connecting to a BMS device.
 */
public record BmsDeviceConfig(
        String host,
        int port,
        int unitId,
        int deviceId,
        long pollIntervalMs,
        java.util.Map<String, String> registerMap
) {}
