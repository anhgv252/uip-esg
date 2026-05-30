-- V30: Force RLS on ai_workflow.workflow_definitions
-- SA review finding: V28 enabled RLS but missing FORCE.
-- Without FORCE, table owner bypasses RLS — production risk for multi-tenant.
-- All other RLS tables (V16) have both ENABLE + FORCE.

ALTER TABLE ai_workflow.workflow_definitions FORCE ROW LEVEL SECURITY;
