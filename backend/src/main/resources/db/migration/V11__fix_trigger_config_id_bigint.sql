-- Fix trigger_config.id: SERIAL (int4) → BIGINT to match JPA entity (Long)
ALTER TABLE workflow.trigger_config ALTER COLUMN id TYPE BIGINT;
