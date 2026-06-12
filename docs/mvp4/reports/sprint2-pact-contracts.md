# Sprint 2 — Pact Inter-Service Contract Documentation

**Sprint:** MVP4 Sprint 2  
**Date:** 2026-06-12  
**Status:** Documentation artifact (Pact tests wired in CI pending broker setup)

---

## 1. Overview

Consumer-Driven Contract Testing (CDC) via Pact ensures that service boundaries stay aligned as the platform evolves. Each consumer defines the interactions it needs; providers verify those expectations without requiring a live consumer.

**Three contract pairs covered in Sprint 2:**

| Consumer | Provider | Transport |
|---|---|---|
| Frontend (React) | ESG API (Spring Boot) | HTTP/REST |
| Frontend (React) | Traffic API (Spring Boot) | HTTP/REST |
| Alert Service (Spring Boot) | Notification Service (Spring Boot) | HTTP/REST |

---

## 2. Consumer Contract Definitions

### 2.1 Frontend → ESG API

**File:** `pacts/frontend-esg_api.json`

```json
{
  "consumer": { "name": "uip-frontend" },
  "provider": { "name": "esg-api" },
  "interactions": [
    {
      "description": "GET ESG summary returns quarterly aggregates",
      "providerState": "ESG data exists for hcm tenant Q1 2026",
      "request": {
        "method": "GET",
        "path": "/api/v1/esg/summary",
        "query": "year=2026&quarter=1",
        "headers": { "Authorization": "Bearer <token>", "X-Tenant-ID": "hcm" }
      },
      "response": {
        "status": 200,
        "headers": { "Content-Type": "application/json" },
        "body": {
          "period": "QUARTERLY",
          "year": 2026,
          "quarter": 1,
          "totalEnergyKwh": { "pact:matcher:type": "type", "value": 15000.5 },
          "totalCarbonTco2e": { "pact:matcher:type": "type", "value": 500.0 },
          "totalWaterM3": { "pact:matcher:type": "type", "value": 800.0 },
          "totalWasteTons": { "pact:matcher:type": "type", "value": 120.0 },
          "sampleCount": { "pact:matcher:type": "type", "value": 5000 }
        }
      }
    },
    {
      "description": "GET ESG energy metrics returns array",
      "providerState": "Energy readings exist for hcm tenant",
      "request": {
        "method": "GET",
        "path": "/api/v1/esg/energy",
        "headers": { "Authorization": "Bearer <token>", "X-Tenant-ID": "hcm" }
      },
      "response": {
        "status": 200,
        "body": {
          "pact:matcher:type": "type",
          "value": [
            {
              "sourceId": "SENSOR-001",
              "metricType": "ENERGY",
              "value": { "pact:matcher:type": "type", "value": 450.0 },
              "unit": "kWh"
            }
          ]
        }
      }
    }
  ]
}
```

### 2.2 Frontend → Traffic API

**File:** `pacts/frontend-traffic_api.json`

```json
{
  "consumer": { "name": "uip-frontend" },
  "provider": { "name": "traffic-api" },
  "interactions": [
    {
      "description": "GET traffic incidents returns paginated OPEN incidents",
      "providerState": "At least one OPEN traffic incident exists",
      "request": {
        "method": "GET",
        "path": "/api/v1/traffic/incidents",
        "query": "status=OPEN&page=0&size=20",
        "headers": { "Authorization": "Bearer <token>" }
      },
      "response": {
        "status": 200,
        "body": {
          "content": {
            "pact:matcher:type": "type",
            "value": [
              {
                "id": { "pact:matcher:type": "regex", "regex": "[0-9a-f-]{36}", "value": "abc" },
                "intersectionId": { "pact:matcher:type": "type", "value": "INT-001" },
                "incidentType": { "pact:matcher:type": "type", "value": "ACCIDENT" },
                "status": "OPEN",
                "latitude": { "pact:matcher:type": "type", "value": 10.7769 },
                "longitude": { "pact:matcher:type": "type", "value": 106.7009 }
              }
            ]
          }
        }
      }
    },
    {
      "description": "GET congestion map returns GeoJSON FeatureCollection",
      "providerState": "Traffic data is available",
      "request": {
        "method": "GET",
        "path": "/api/v1/traffic/congestion-map",
        "headers": { "Authorization": "Bearer <token>" }
      },
      "response": {
        "status": 200,
        "body": {
          "type": "FeatureCollection",
          "features": {
            "pact:matcher:type": "type",
            "value": []
          }
        }
      }
    }
  ]
}
```

### 2.3 Alert Service → Notification Service

**File:** `pacts/alert_service-notification_service.json`

```json
{
  "consumer": { "name": "alert-service" },
  "provider": { "name": "notification-service" },
  "interactions": [
    {
      "description": "POST critical alert triggers push notification",
      "providerState": "Notification service is up and tenant hcm has FCM tokens registered",
      "request": {
        "method": "POST",
        "path": "/internal/notifications/push",
        "headers": { "Content-Type": "application/json", "X-Internal-Token": "<token>" },
        "body": {
          "tenantId": "hcm",
          "title": { "pact:matcher:type": "type", "value": "CRITICAL Alert: AQI Threshold" },
          "body": { "pact:matcher:type": "type", "value": "AQI 350 exceeds threshold 200" },
          "severity": "CRITICAL",
          "alertId": { "pact:matcher:type": "regex", "regex": "[0-9a-f-]{36}", "value": "abc" }
        }
      },
      "response": {
        "status": 202,
        "body": {
          "accepted": true,
          "messageId": { "pact:matcher:type": "type", "value": "msg-001" }
        }
      }
    }
  ]
}
```

---

## 3. Provider State Setup

Each provider test must set up the described state before verifying. Spring Boot example using `@State` annotation from `au.com.dius.pact.provider.junitsupport`:

```java
// ESG API provider verification (BackendProviderPactTest extends existing setup)
@State("ESG data exists for hcm tenant Q1 2026")
void esgDataExists() {
    EsgSummaryDto dto = EsgSummaryDto.builder()
        .period("QUARTERLY").year(2026).quarter(1)
        .totalEnergyKwh(15000.5).totalCarbonTco2e(500.0)
        .totalWaterM3(800.0).totalWasteTons(120.0).sampleCount(5000L)
        .build();
    when(esgService.getSummary(eq("hcm"), anyString(), anyInt(), anyInt())).thenReturn(dto);
}

@State("At least one OPEN traffic incident exists")
void openIncidentExists() {
    TrafficIncidentDto dto = TrafficIncidentDto.builder()
        .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        .intersectionId("INT-001").incidentType("ACCIDENT").status("OPEN")
        .latitude(10.7769).longitude(106.7009).occurredAt(LocalDateTime.now())
        .build();
    Page<TrafficIncidentDto> page = new PageImpl<>(List.of(dto));
    when(trafficService.getIncidents(eq("OPEN"), any())).thenReturn(page);
}
```

---

## 4. CI Verification Steps

### Step 1 — Consumer generates pact files

```bash
# Run consumer Pact tests (generate JSON files under target/pacts/)
./gradlew test --tests "*ConsumerPactTest"
```

Generated files go to `target/pacts/`. These are uploaded to the Pact Broker.

### Step 2 — Publish pacts to Pact Broker

```bash
# Using pact-jvm-provider-gradle plugin or pactbroker-publish goal
./gradlew pactPublish \
  -PpactBrokerUrl=https://pact-broker.uip.internal \
  -PpactBrokerToken=${PACT_BROKER_TOKEN} \
  -Ppact.version=${GIT_COMMIT_SHA}
```

### Step 3 — Provider verifies contracts

```bash
# Provider test reads pact file from broker and verifies each interaction
./gradlew test --tests "*ProviderPactTest" \
  -Ppact.verifier.publishResults=true \
  -Ppact.provider.version=${GIT_COMMIT_SHA}
```

### Step 4 — Can-I-Deploy gate

```bash
# Block deploy if consumer/provider contract is broken
pact-broker can-i-deploy \
  --pacticipant uip-frontend \
  --version ${GIT_COMMIT_SHA} \
  --to-environment staging
```

### GitHub Actions CI fragment

```yaml
- name: Run contract tests
  run: ./gradlew test --tests "*PactTest" --tests "*ContractTest" -Ptags=contract

- name: Publish pacts
  run: ./gradlew pactPublish
  env:
    PACT_BROKER_TOKEN: ${{ secrets.PACT_BROKER_TOKEN }}

- name: Can-I-Deploy check
  run: |
    pact-broker can-i-deploy \
      --pacticipant uip-backend \
      --version ${{ github.sha }} \
      --to-environment ${{ github.event.inputs.environment || 'staging' }}
  env:
    PACT_BROKER_BASE_URL: ${{ secrets.PACT_BROKER_URL }}
```

---

## 5. Quality Gates Added

| Gate | Condition | Blocks |
|---|---|---|
| `contract-tests` | All `@Tag("contract")` tests pass | PR merge |
| `pact-publish` | Pact JSON files uploaded to broker | staging deploy |
| `can-i-deploy` | Provider has verified current consumer version | prod deploy |
| `contract-count` | Total `@Tag("contract")` tests ≥ 30 | CI build |
