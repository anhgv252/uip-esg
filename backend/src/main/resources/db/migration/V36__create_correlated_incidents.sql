-- M4-COR-01: Correlated incidents — persisted output of Flink CEP correlation window
-- Each row represents a group of sensor events from ≥3 distinct sensor types within a
-- time window that exceeded the correlation score threshold.

CREATE TABLE IF NOT EXISTS correlated_incidents (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    building_id      VARCHAR(100),
    sensor_types     TEXT,                              -- JSON array e.g. '["AQI","FLOOD","NOISE"]'
    correlation_score DOUBLE PRECISION NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    detected_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    event_count      INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_correlated_incidents_building
    ON correlated_incidents (building_id)
    WHERE building_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_correlated_incidents_detected_at
    ON correlated_incidents (detected_at DESC);

CREATE INDEX IF NOT EXISTS idx_correlated_incidents_status
    ON correlated_incidents (status);
