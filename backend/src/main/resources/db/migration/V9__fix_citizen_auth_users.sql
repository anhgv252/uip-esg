-- V9: Fix missing app_users entries for citizen2 and citizen3
-- V7 seeded citizens.citizen_accounts for citizen1/2/3 but only citizen1 was
-- manually registered in app_users during a prior demo run.
-- citizen2 and citizen3 had no auth record → could not login.
--
-- Passwords (BCrypt cost=12): citizen_Dev#2026!
--   → $2a$12$PNchUMuWmIKTRq/gi33.Zu744k2c/D.S1DvGXFin/qO/j1QuM6sg2

INSERT INTO public.app_users (username, email, password_hash, role)
VALUES
    ('citizen2', 'citizen2@uip.city', '$2a$12$PNchUMuWmIKTRq/gi33.Zu744k2c/D.S1DvGXFin/qO/j1QuM6sg2', 'ROLE_CITIZEN'),
    ('citizen3', 'citizen3@uip.city', '$2a$12$PNchUMuWmIKTRq/gi33.Zu744k2c/D.S1DvGXFin/qO/j1QuM6sg2', 'ROLE_CITIZEN')
ON CONFLICT (username) DO NOTHING;
