package com.uip.backend.bms.service;

import com.uip.backend.bms.api.dto.BmsDeviceRequest;
import com.uip.backend.bms.api.dto.BmsDeviceResponse;
import com.uip.backend.bms.domain.BmsDevice;
import com.uip.backend.bms.domain.BmsProtocol;
import com.uip.backend.bms.repository.BmsDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BmsDeviceService {

    private final BmsDeviceRepository bmsDeviceRepository;

    @Transactional(readOnly = true)
    public List<BmsDeviceResponse> listDevices(String tenantId) {
        return bmsDeviceRepository.findByTenantId(tenantId).stream()
                .map(BmsDeviceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<BmsDeviceResponse> getDevice(String tenantId, UUID id) {
        return bmsDeviceRepository.findById(id)
                .filter(d -> d.getTenantId().equals(tenantId))
                .map(BmsDeviceResponse::from);
    }

    @Transactional
    public BmsDevice upsertDevice(String tenantId, BmsDeviceRequest request) {
        BmsProtocol protocol = BmsProtocol.valueOf(request.protocol());
        return bmsDeviceRepository.findByTenantIdAndDeviceName(tenantId, request.deviceName())
                .map(existing -> {
                    updateFields(existing, request, protocol);
                    return bmsDeviceRepository.save(existing);
                })
                .orElseGet(() -> {
                    BmsDevice device = new BmsDevice();
                    device.setTenantId(tenantId);
                    device.setDeviceName(request.deviceName());
                    device.setProtocol(protocol);
                    fillFields(device, request);
                    return bmsDeviceRepository.save(device);
                });
    }

    @Transactional
    public BmsDevice updateDevice(String tenantId, UUID id, BmsDeviceRequest request) {
        BmsDevice device = bmsDeviceRepository.findById(id)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + id));
        device.setDeviceName(request.deviceName());
        device.setProtocol(BmsProtocol.valueOf(request.protocol()));
        updateFields(device, request, device.getProtocol());
        return bmsDeviceRepository.save(device);
    }

    @Transactional
    public void deleteDevice(String tenantId, UUID id) {
        BmsDevice device = bmsDeviceRepository.findById(id)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + id));
        bmsDeviceRepository.delete(device);
    }

    @Transactional
    public BmsDevice registerDiscoveredDevice(String tenantId, String deviceName, BmsProtocol protocol,
                                               String host, Integer port, Integer deviceId,
                                               Map<String, Object> metadata) {
        return bmsDeviceRepository.findByTenantIdAndDeviceName(tenantId, deviceName)
                .map(existing -> {
                    existing.setHost(host);
                    existing.setPort(port);
                    existing.setDeviceId(deviceId);
                    existing.setStatus("ONLINE");
                    existing.setLastSeen(java.time.Instant.now());
                    if (metadata != null) existing.getMetadata().putAll(metadata);
                    return bmsDeviceRepository.save(existing);
                })
                .orElseGet(() -> {
                    BmsDevice device = new BmsDevice();
                    device.setTenantId(tenantId);
                    device.setDeviceName(deviceName);
                    device.setProtocol(protocol);
                    device.setHost(host);
                    device.setPort(port);
                    device.setDeviceId(deviceId);
                    device.setStatus("ONLINE");
                    device.setLastSeen(java.time.Instant.now());
                    if (metadata != null) device.setMetadata(metadata);
                    return bmsDeviceRepository.save(device);
                });
    }

    private void fillFields(BmsDevice device, BmsDeviceRequest request) {
        device.setHost(request.host());
        device.setPort(request.port());
        device.setUnitId(request.unitId());
        device.setDeviceId(request.deviceId());
        device.setPollInterval(request.pollInterval() != null ? request.pollInterval() : 5000);
        if (request.metadata() != null) device.setMetadata(request.metadata());
    }

    private void updateFields(BmsDevice device, BmsDeviceRequest request, BmsProtocol protocol) {
        device.setProtocol(protocol);
        device.setHost(request.host());
        device.setPort(request.port());
        device.setUnitId(request.unitId());
        device.setDeviceId(request.deviceId());
        if (request.pollInterval() != null) device.setPollInterval(request.pollInterval());
        if (request.metadata() != null) device.setMetadata(request.metadata());
    }
}
