-- V6: Create Traffic Module Entities
-- Traffic incidents and vehicle count tracking

-- Drop legacy traffic_counts hypertable from POC (wrong schema: uses 'timestamp' instead of 'recorded_at')
DROP TABLE IF EXISTS traffic.traffic_counts CASCADE;

CREATE TABLE IF NOT EXISTS traffic.traffic_incidents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intersection_id VARCHAR(100),
    incident_type VARCHAR(50) NOT NULL,
    description TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    occurred_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_traffic_incidents_status ON traffic.traffic_incidents(status);
CREATE INDEX IF NOT EXISTS idx_traffic_incidents_occurred_at ON traffic.traffic_incidents(occurred_at DESC);

CREATE TABLE IF NOT EXISTS traffic.traffic_counts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intersection_id VARCHAR(100) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    vehicle_count INTEGER NOT NULL DEFAULT 0,
    vehicle_type VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_traffic_counts_intersection ON traffic.traffic_counts(intersection_id);
CREATE INDEX IF NOT EXISTS idx_traffic_counts_recorded_at ON traffic.traffic_counts(recorded_at DESC);

-- Seed traffic incidents for testing
INSERT INTO traffic.traffic_incidents 
    (intersection_id, incident_type, description, latitude, longitude, status, occurred_at)
VALUES
    ('INT-001', 'ACCIDENT', 'Minor collision at Nguyen Hue - Le Loi', 10.7838, 106.7026, 'OPEN', NOW() - INTERVAL '2 hours'),
    ('INT-002', 'CONGESTION', 'Heavy traffic on Dien Bien Phu', 10.7850, 106.7015, 'OPEN', NOW() - INTERVAL '1 hour'),
    ('INT-003', 'ROADWORK', 'Road maintenance on Nam Ky Khoi Nghia', 10.7900, 106.7100, 'RESOLVED', NOW() - INTERVAL '5 hours'),
    ('INT-004', 'ACCIDENT', 'Vehicle breakdown on Truong Chinh', 10.7750, 106.7050, 'OPEN', NOW() - INTERVAL '30 minutes'),
    ('INT-005', 'CONGESTION', 'Rush hour backup at Thu Duc bridge', 10.7700, 106.7200, 'OPEN', NOW() - INTERVAL '45 minutes');

-- Seed some vehicle counts
INSERT INTO traffic.traffic_counts (intersection_id, recorded_at, vehicle_count, vehicle_type)
VALUES
    ('INT-001', NOW() - INTERVAL '1 hour', 450, 'CAR'),
    ('INT-001', NOW() - INTERVAL '1 hour', 120, 'MOTORCYCLE'),
    ('INT-002', NOW() - INTERVAL '45 minutes', 380, 'CAR'),
    ('INT-002', NOW() - INTERVAL '45 minutes', 200, 'MOTORCYCLE'),
    ('INT-003', NOW() - INTERVAL '30 minutes', 200, 'CAR'),
    ('INT-004', NOW() - INTERVAL '30 minutes', 320, 'CAR'),
    ('INT-005', NOW() - INTERVAL '15 minutes', 550, 'CAR'),
    ('INT-005', NOW() - INTERVAL '15 minutes', 180, 'MOTORCYCLE');
