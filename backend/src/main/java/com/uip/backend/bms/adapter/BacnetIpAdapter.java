package com.uip.backend.bms.adapter;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.Real;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.util.RequestUtils;
import com.uip.backend.bms.api.dto.BmsCommand;
import com.uip.backend.bms.domain.BmsProtocol;
import com.uip.backend.bms.domain.BmsReading;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class BacnetIpAdapter implements BmsProtocolAdapter {

    private final Map<String, String> propertyMap;
    private LocalDevice localDevice;
    private BmsDeviceConfig config;
    private volatile boolean connected;

    public BacnetIpAdapter(Map<String, String> propertyMap) {
        this.propertyMap = propertyMap != null ? propertyMap : Map.of();
    }

    @Override
    public String getProtocol() {
        return BmsProtocol.BACNET_IP.name();
    }

    @Override
    public void connect(BmsDeviceConfig config) {
        this.config = config;
        try {
            IpNetwork network = new IpNetworkBuilder()
                    .withLocalBindAddress("0.0.0.0")
                    .withBroadcast("255.255.255.255", 24)
                    .withPort(config.port() > 0 ? config.port() : 47808)
                    .build();
            Transport transport = new DefaultTransport(network);
            localDevice = new LocalDevice(config.unitId(), transport);
            localDevice.initialize();
            connected = true;
            log.info("BACnet initialized: deviceId={}", config.deviceId());
        } catch (Exception e) {
            connected = false;
            throw new BmsAdapterException("BACnet init failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        if (localDevice != null) {
            try {
                localDevice.terminate();
            } catch (Exception e) {
                log.warn("BACnet terminate error: {}", e.getMessage());
            }
            connected = false;
        }
    }

    @Override
    public List<BmsReading> poll() {
        if (!connected || localDevice == null) {
            throw new BmsAdapterException("BACnet not connected");
        }

        List<BmsReading> readings = new ArrayList<>();
        try {
            RemoteDevice remoteDevice = localDevice.getRemoteDeviceBlocking(config.deviceId());

            for (Map.Entry<String, String> entry : propertyMap.entrySet()) {
                String readingType = entry.getKey();
                String[] parts = entry.getValue().split(":");
                String objectType = parts.length > 0 ? parts[0] : "analogInput";
                int instanceNumber = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                String unit = parts.length > 2 ? parts[2] : "";

                try {
                    ObjectIdentifier oid = new ObjectIdentifier(
                            ObjectType.forName(objectType), instanceNumber);

                    Encodable result = RequestUtils.readProperty(
                            localDevice, remoteDevice, oid, PropertyIdentifier.presentValue, null);

                    if (result instanceof Real realVal) {
                        readings.add(new BmsReading(readingType, realVal.floatValue(), unit, Instant.now()));
                    } else if (result instanceof UnsignedInteger uintVal) {
                        readings.add(new BmsReading(readingType, uintVal.intValue(), unit, Instant.now()));
                    }
                } catch (Exception e) {
                    log.warn("BACnet read failed: type={} obj={}: {}", readingType, instanceNumber, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("BACnet device {} not found: {}", config.deviceId(), e.getMessage());
        }
        return readings;
    }

    @Override
    public boolean isAlive() {
        return connected && localDevice != null;
    }

    @Override
    public void sendCommand(BmsCommand command) {
        if (!connected || localDevice == null) {
            throw new BmsAdapterException("BACnet not connected");
        }
        log.info("BACnet command: deviceId={} cmd={}", config.deviceId(), command.commandType());
    }
}
