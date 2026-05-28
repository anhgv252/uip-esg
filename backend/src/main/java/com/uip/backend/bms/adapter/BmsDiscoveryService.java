package com.uip.backend.bms.adapter;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.util.RemoteDeviceDiscoverer;
import com.uip.backend.bms.domain.BmsDevice;
import com.uip.backend.bms.domain.BmsProtocol;
import com.uip.backend.bms.service.BmsDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BmsDiscoveryService {

    private static final int SCAN_TIMEOUT_MS = 10000;
    private static final int DEFAULT_BACNET_PORT = 47808;

    private final BmsDeviceService bmsDeviceService;

    @Value("${bms.discovery.enabled:false}")
    private boolean discoveryEnabled;

    @Value("${bms.discovery.broadcast:255.255.255.255}")
    private String broadcastAddress;

    @Value("${bms.discovery.local-device-id:100}")
    private int localDeviceId;

    @Value("${bms.discovery.tenant-id:default}")
    private String discoveryTenantId;

    @Scheduled(fixedDelayString = "${bms.discovery.interval-ms:300000}", initialDelayString = "${bms.discovery.initial-delay-ms:60000}")
    public void scheduledDiscovery() {
        if (!discoveryEnabled) {
            return;
        }
        log.info("Scheduled BACnet Who-Is discovery starting...");
        try {
            List<BmsDevice> discovered = discoverDevices(discoveryTenantId, broadcastAddress, localDeviceId);
            log.info("Scheduled discovery complete: {} devices found", discovered.size());
        } catch (Exception e) {
            log.error("Scheduled discovery failed: {}", e.getMessage());
        }
    }

    public List<BmsDevice> discoverDevices(String tenantId, String broadcastAddress, int localDeviceId) {
        log.info("BACnet Who-Is discovery started: tenant={} broadcast={}", tenantId, broadcastAddress);

        Transport transport = new DefaultTransport(
                new IpNetworkBuilder()
                        .withLocalBindAddress("0.0.0.0")
                        .withBroadcast(broadcastAddress != null ? broadcastAddress : "255.255.255.255", 24)
                        .withPort(DEFAULT_BACNET_PORT)
                        .build());

        LocalDevice localDevice = new LocalDevice(localDeviceId, transport);
        List<BmsDevice> discovered = new ArrayList<>();

        try {
            localDevice.initialize();

            try (RemoteDeviceDiscoverer discoverer = new RemoteDeviceDiscoverer(localDevice)) {
                discoverer.start();
                Thread.sleep(SCAN_TIMEOUT_MS);
                discoverer.stop();

                List<RemoteDevice> remoteDevices = discoverer.getRemoteDevices();
                log.info("BACnet discovery found {} devices", remoteDevices.size());

                for (RemoteDevice rd : remoteDevices) {
                    try {
                        String deviceName = "BACNET-" + rd.getInstanceNumber();
                        String host = rd.getAddress().getMacAddress().toString();

                        BmsDevice device = bmsDeviceService.registerDiscoveredDevice(
                                tenantId,
                                deviceName,
                                BmsProtocol.BACNET_IP,
                                host,
                                DEFAULT_BACNET_PORT,
                                rd.getInstanceNumber(),
                                Map.of("vendorId", rd.getVendorIdentifier(),
                                       "vendorName", rd.getVendorName() != null ? rd.getVendorName() : "unknown"));

                        discovered.add(device);
                        log.info("Discovered BACnet device: name={} id={} vendor={}",
                                deviceName, rd.getInstanceNumber(), rd.getVendorName());
                    } catch (Exception e) {
                        log.warn("Failed to register discovered device {}: {}",
                                rd.getInstanceNumber(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("BACnet discovery failed: {}", e.getMessage());
        } finally {
            try {
                localDevice.terminate();
            } catch (Exception e) {
                log.warn("LocalDevice terminate error: {}", e.getMessage());
            }
        }

        return discovered;
    }

    @Async
    public void discoverDevicesAsync(String tenantId, String broadcastAddress, int localDeviceId) {
        discoverDevices(tenantId, broadcastAddress, localDeviceId);
    }
}
