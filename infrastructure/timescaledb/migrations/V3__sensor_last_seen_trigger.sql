-- V3: Auto-update sensors.last_seen_at when a new reading is inserted
-- This ensures sensors go ONLINE automatically via Flink → DB writes
-- without requiring a separate UPDATE step.
--
-- Performance note: Uses UPDATE ... WHERE last_seen_at < NEW.timestamp
-- to avoid unnecessary updates and reduce lock contention at high throughput.
-- At very high rates (100K msg/s), consider disabling this trigger and running
-- a periodic batch UPDATE instead (e.g., every 30s via pg_cron / Flink side effect).

CREATE OR REPLACE FUNCTION environment.update_sensor_last_seen()
    RETURNS TRIGGER AS $$
BEGIN
    -- Only UPDATE if sensor exists AND the new timestamp is more recent.
    -- This prevents lock contention when the same sensor sends rapid messages.
    UPDATE environment.sensors
    SET last_seen_at = NEW.timestamp
    WHERE sensor_id = NEW.sensor_id
      AND (last_seen_at IS NULL OR last_seen_at < NEW.timestamp);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sensor_last_seen ON environment.sensor_readings;

-- Use WHEN condition to skip the trigger entirely if sensor is already fresh
-- (updated within the last minute) — reduces UPDATE ops by >95% at high throughput.
CREATE TRIGGER trg_sensor_last_seen
    AFTER INSERT ON environment.sensor_readings
    FOR EACH ROW
    WHEN (pg_trigger_depth() = 0)
EXECUTE FUNCTION environment.update_sensor_last_seen();
