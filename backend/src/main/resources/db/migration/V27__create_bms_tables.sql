-- V27: BMS schema — devices + readings tables (ADR-029)
-- Sprint 5: BMS SDK full integration

-- Create bms schema
CREATE SCHEMA IF NOT EXISTS bms;

-- BMS devices table
CREATE TABLE bms.bms_devices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       TEXT NOT NULL DEFAULT 'default',
    device_name     VARCHAR(255) NOT NULL,
    protocol        VARCHAR(20) NOT NULL CHECK (protocol IN ('MODBUS_TCP', 'BACNET_IP', 'MQTT', 'MANUAL')),
    host            VARCHAR(255),
    port            INTEGER,
    unit_id         INTEGER,
    device_id       INTEGER,
    poll_interval   INTEGER DEFAULT 5000,
    last_seen       TIMESTAMPTZ,
    status          VARCHAR(20) DEFAULT 'UNKNOWN',
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now(),
    UNIQUE(tenant_id, device_name)
);

-- BMS raw readings table
CREATE TABLE bms.bms_readings_raw (
    id              BIGSERIAL,
    tenant_id       TEXT NOT NULL DEFAULT 'default',
    device_id       UUID NOT NULL REFERENCES bms.bms_devices(id),
    reading_type    VARCHAR(100) NOT NULL,
    value           DOUBLE PRECISION NOT NULL,
    unit            VARCHAR(20),
    timestamp       TIMESTAMPTZ NOT NULL,
    ingested_at     TIMESTAMPTZ DEFAULT now()
);

-- Indexes
CREATE INDEX idx_bms_devices_tenant ON bms.bms_devices(tenant_id);
CREATE INDEX idx_bms_devices_protocol ON bms.bms_devices(protocol);
CREATE INDEX idx_bms_readings_device ON bms.bms_readings_raw(device_id);
CREATE INDEX idx_bms_readings_timestamp ON bms.bms_readings_raw(timestamp);
CREATE INDEX idx_bms_readings_tenant ON bms.bms_readings_raw(tenant_id);

-- RLS policies
ALTER TABLE bms.bms_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE bms.bms_readings_raw ENABLE ROW LEVEL SECURITY;

CREATE POLICY bms_devices_tenant_isolation ON bms.bms_devices
    USING (tenant_id = current_setting('app.current_tenant', true));

CREATE POLICY bms_readings_tenant_isolation ON bms.bms_readings_raw
    USING (tenant_id = current_setting('app.current_tenant', true));
