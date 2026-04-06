-- V3: Auto-update sensors.last_seen_at when a new reading is inserted
-- This ensures sensors go ONLINE automatically via Flink → DB writes
-- without requiring a separate UPDATE step.

CREATE OR REPLACE FUNCTION environment.update_sensor_last_seen()
    RETURNS TRIGGER AS $$
BEGIN
    UPDATE environment.sensors
    SET last_seen_at = NEW.timestamp
    WHERE sensor_id = NEW.sensor_id
      AND (last_seen_at IS NULL OR last_seen_at < NEW.timestamp);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sensor_last_seen ON environment.sensor_readings;

CREATE TRIGGER trg_sensor_last_seen
    AFTER INSERT ON environment.sensor_readings
    FOR EACH ROW
EXECUTE FUNCTION environment.update_sensor_last_seen();
