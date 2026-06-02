-- V33: Add alert:escalate scope to admin and operator users
-- V17 only granted alert:ack but omitted alert:escalate for admin and operator.
-- AlertsPage (frontend) disables the Escalate button when this scope is missing.
INSERT INTO public.app_user_scopes (user_id, scope)
SELECT id, 'alert:escalate'
FROM public.app_users WHERE role IN ('ROLE_ADMIN', 'ROLE_OPERATOR')
ON CONFLICT DO NOTHING;
