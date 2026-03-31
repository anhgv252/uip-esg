-- Flyway copies of infrastructure migrations
-- These are applied by Spring Boot Flyway at startup
-- Source: infrastructure/timescaledb/migrations/

-- This file intentionally references the canonical SQL files.
-- In Docker the init scripts run on TimescaleDB directly.
-- Flyway here tracks the schema version for Spring Boot.

-- NOTE: Flyway migration files live in src/main/resources/db/migration/
-- Files are prefixed V1__, V2__, etc. matching the infrastructure migrations.
