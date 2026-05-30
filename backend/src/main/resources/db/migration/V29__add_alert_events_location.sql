-- V29: Add location column to alert_events for flood alert map overlay
-- Sprint 6: B2-2 FloodAlertConsumer stores district as location
ALTER TABLE alerts.alert_events ADD COLUMN IF NOT EXISTS location VARCHAR(200);
