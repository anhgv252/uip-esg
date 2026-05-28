# ADR-029: BMS Protocol Adapter — Modbus TCP + BACnet/IP

**Status:** APPROVED
**Date:** 2026-05-27
**Sprint:** MVP3-5
**Author:** Solution Architect
**Reviewers:** Backend-1, Backend-2, PO

---

## Context

Sprint 5 cần implement BMS (Building Management System) SDK đầy đủ để integrate với building devices qua 2 protocol:
- **Modbus TCP** — industrial standard, polling-based
- **BACnet/IP** — building automation standard, discovery + polling

Yêu cầu:
1. Unified interface cho cả 2 protocol
2. Circuit Breaker per adapter để resilient
3. Device Registry quản lý tất cả devices (manual + auto-discovered)
4. Kafka producer đẩy readings lên topic chung
5. Device Control API → EMQX MQTT → thiết bị

## Decision

### D1: Strategy Pattern — BmsProtocolAdapter Interface

```java
public interface BmsProtocolAdapter {
    String getProtocol();                    // "MODBUS_TCP" | "BACNET_IP"
    void connect(BmsDeviceConfig config);
    void disconnect();
    List<BmsReading> poll();
    boolean isAlive();
    void sendCommand(BmsCommand command);
}
```

Mỗi protocol là 1 implementation: `ModbusTcpAdapter`, `BacnetIpAdapter`.

`BmsAdapterRegistry` quản lý map `(protocol, deviceId) → adapter instance`.

### D2: Libraries

| Protocol | Library | Version | License | Maven Coords |
|----------|---------|---------|---------|-------------|
| Modbus TCP | j2mod | 3.2.1 | Apache 2.0 | `com.ghgarris:j2mod:3.2.1` |
| BACnet/IP | BACnet4J | 6.0.1 | GPL-3.0 (code) / Commercial (production) | `com.infiniteautomation:bacnet4j:6.0.1` |

**BACnet4J Maven repo:** `https://maven.mangoautomation.net/repository/ias-release/` (public, no auth)

**Commercial license decision:** PO approved mua commercial license từ RadixIoT. Code trên GitHub là GPL-3.0, nhưng production deploy dùng commercial license. Dev không block — JAR download từ Maven repo công khai.

### D3: Kafka Topic Naming

Follow UIP convention (`UIP.{module}.{event}.{version}`):

| Topic | Purpose | Key |
|-------|---------|-----|
| `UIP.bms.reading.raw.v1` | BMS readings from all adapters | `tenantId + deviceId` |
| `UIP.bms.reading.raw.v1.dlq` | Dead Letter Queue for failed readings | `tenantId + deviceId` |
| `bms.commands.{tenantId}.{deviceId}` | MQTT command topic (EMQX) | — |

### D4: Circuit Breaker Config (Resilience4j)

| Instance | Config |
|----------|--------|
| `bms-modbus-tcp` | `failureRateThreshold=50%`, `slowCallDurationThreshold=5s`, `slidingWindowSize=10`, `waitDurationInOpenState=60s` |
| `bms-bacnet-ip` | Same as modbus |

CB wrap `poll()` method trên mỗi adapter. Khi CB open → readings go to DLQ.

### D5: Adapter Lifecycle

```
BmsAdapterRegistry
  ├── @PostConstruct: load BmsDevice from DB → create adapters → connect()
  ├── Scheduled poll: each adapter.poll() → Kafka producer
  ├── @PreDestroy: disconnect() all adapters
  └── Discovery: BACnet Who-Is → new device → register → create adapter
```

### D6: Package Structure

```
com.uip.backend.bms/
├── adapter/
│   ├── BmsProtocolAdapter.java          (interface)
│   ├── BmsAdapterRegistry.java          (adapter lifecycle)
│   ├── ModbusTcpAdapter.java
│   ├── BacnetIpAdapter.java
│   └── BmsDiscoveryService.java         (BACnet Who-Is)
├── api/
│   ├── BmsDeviceController.java
│   ├── BmsDeviceCommandController.java
│   └── dto/
│       ├── BmsDeviceConfig.java
│       ├── BmsDeviceResponse.java
│       ├── BmsReadingEvent.java
│       └── BmsCommand.java
├── config/
│   ├── BmsAutoConfiguration.java
│   └── BmsProperties.java
├── domain/
│   ├── BmsDevice.java                   (entity, @TenantAware)
│   ├── BmsReading.java                  (reading value object)
│   └── BmsProtocol.java                 (enum: MODBUS_TCP, BACNET_IP, MANUAL)
├── kafka/
│   ├── BmsReadingKafkaProducer.java
│   └── BmsCommandAckConsumer.java
└── repository/
    ├── BmsDeviceRepository.java
    └── BmsReadingRepository.java
```

### D7: Dependency Diagram

```
BmsDeviceController ──→ BmsDeviceService ──→ BmsAdapterRegistry
                                              ├── ModbusTcpAdapter (j2mod)
                                              └── BacnetIpAdapter (BACnet4J)
                         BmsDiscoveryService ──→ BACnet4J RemoteDeviceDiscovery
                         BmsReadingKafkaProducer ──→ Kafka UIP.bms.reading.raw.v1
                         BmsDeviceCommandController ──→ EMQX MQTT
```

## Consequences

### Positive
- **Unified interface** — thêm protocol mới chỉ cần implement `BmsProtocolAdapter`
- **Resilient** — CB per adapter, failure không cascade
- **BACnet4J production-ready** — không cần tự implement BACnet/IP stack (tiết kiệm ~3 SP)
- **j2mod Apache 2.0** — no license concern

### Negative
- **BACnet4J commercial license** — phụ thuộc RadixIoT renewal, nhưng code luôn available từ Maven repo
- **j2mod ModbusTCPMaster not thread-safe** — mitigation: single-threaded scheduler per device group
- **Socket leak risk** — mitigation: `@PreDestroy` + `DisposableBean` cleanup

### Risks

| ID | Risk | Probability | Impact | Mitigation |
|----|------|-------------|--------|-----------|
| R1 | BACnet4J commercial license renewal | Low | Medium | Adapter isolation — swap implementation if needed |
| R2 | j2mod thread safety | Low | Low | Single-threaded poll per device group |
| R3 | Socket leak on stale connections | Low | Medium | `@PreDestroy` + `DisposableBean` |

## References

- ADR-032: Forecast Service (existing pattern: `ForecastPort` interface + adapters)
- Sprint 5 Plan: `docs/mvp3/project/sprint5-plan.md`
- BACnet4J: https://github.com/MangoAutomation/BACnet4J
- j2mod: https://github.com/steveohara/j2mod
