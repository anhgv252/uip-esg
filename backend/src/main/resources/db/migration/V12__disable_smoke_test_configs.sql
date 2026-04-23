-- T-UAT-BE-01: Disable smoke/test trigger configs before UAT
-- Prevents test_smoke_scenario from accidentally firing in UAT environment.
-- Safe no-op if row does not exist.

UPDATE workflow.trigger_config
SET enabled = false
WHERE scenario_key = 'test_smoke_scenario';
