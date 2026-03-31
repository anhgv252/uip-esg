-- V1: Create app_users table for JWT authentication
-- Used by Backend (Spring Boot) — separate from infrastructure TimescaleDB migrations

CREATE TABLE IF NOT EXISTS public.app_users (
    id            UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    username      VARCHAR(50)   NOT NULL UNIQUE,
    email         VARCHAR(255)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,
    role          VARCHAR(30)   NOT NULL DEFAULT 'ROLE_CITIZEN',
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_app_users_username ON public.app_users (username);
CREATE INDEX idx_app_users_email    ON public.app_users (email);

-- Seed default users (passwords are BCrypt-encoded, cost=12)
-- admin_Dev#2026!    → $2a$12$JS7kx3KnMjNWQjx1bJpCWOxqUfUsSu.r6Vy6FLhP3SzB6w/3v6i9K
-- operator_Dev#2026! → $2a$12$UTezWw6W1rt5sGiS4.6aCe76vrIVlbNzEA94oqjB/iCtYT0SmvkRq
-- citizen_Dev#2026!  → $2a$12$PNchUMuWmIKTRq/gi33.Zu744k2c/D.S1DvGXFin/qO/j1QuM6sg2
INSERT INTO public.app_users (username, email, password_hash, role) VALUES
    ('admin',    'admin@uip.local',    '$2a$12$JS7kx3KnMjNWQjx1bJpCWOxqUfUsSu.r6Vy6FLhP3SzB6w/3v6i9K', 'ROLE_ADMIN'),
    ('operator', 'operator@uip.local', '$2a$12$UTezWw6W1rt5sGiS4.6aCe76vrIVlbNzEA94oqjB/iCtYT0SmvkRq', 'ROLE_OPERATOR'),
    ('citizen',  'citizen@uip.local',  '$2a$12$PNchUMuWmIKTRq/gi33.Zu744k2c/D.S1DvGXFin/qO/j1QuM6sg2', 'ROLE_CITIZEN')
ON CONFLICT (username) DO NOTHING;
