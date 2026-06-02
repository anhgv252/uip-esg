-- V32: Ensure citizen1 is in public.app_users with the correct password
-- V1 seeds admin/operator/citizen. V9 adds citizen2/citizen3. citizen1 was manually
-- registered during a prior demo run with an unknown password — this migration
-- upserts citizen1 with the standard test password: citizen_Dev#2026!
-- Password (BCrypt cost=12): citizen_Dev#2026!
INSERT INTO public.app_users (username, email, password_hash, role)
VALUES ('citizen1', 'citizen1@uip.city', '$2a$12$PNchUMuWmIKTRq/gi33.Zu744k2c/D.S1DvGXFin/qO/j1QuM6sg2', 'ROLE_CITIZEN')
ON CONFLICT (username) DO UPDATE
  SET password_hash = EXCLUDED.password_hash,
      email        = EXCLUDED.email,
      role         = EXCLUDED.role;
