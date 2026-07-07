-- H173: Align microservice_registry to H159 12-service topology.
-- Source of authority: abao-platform/docs/TASK-G0-04H159-G0-01-SERVICE-COUNT-AUTHORITY-AND-API-CONTRACT-PLACEMENT-DECISION-REPORT.md
--
-- V01_003__init_abao_tenant.sql originally seeded 14 rows into microservice_registry:
--   1 gateway + 11 business + 2 common = 14.
-- H159 decided the authoritative G0-01 topology is the 12-service final roadmap
-- from 00-50份PRD总索引与开发路线图.md:
--   1 gateway + 9 business + 2 common = 12.
-- The two stale rows to remove are:
--   - geihou-module-market   (port 48084, subsystem_id 4)
--   - geihou-module-strategy (port 48085, subsystem_id 5)
--
-- This migration is idempotent: re-running it on a 12-service DB is a no-op
-- because the WHERE clause simply matches zero rows.
--
-- Scope boundary (DO NOT cross in this migration):
--   - Do NOT delete geihou-module-strategy-track (subsystem 10, port 48090).
--     It is a different service from geihou-module-strategy and stays in the
--     12-service topology.
--   - Do NOT modify tenant_subsystem_enabled in this migration. Subsystem
--     IDs 4 and 5 become orphaned in that table as a known follow-up; it is
--     out of scope for H173 and must be handled by a later, dedicated task
--     package.
--   - Do NOT modify V01_003. Flyway forbids editing applied migrations;
--     this V03_029 migration is the corrective forward step.
--   - Do NOT touch pom-geihou.xml, workflow YAML, gateway source, or any
--     per-service Application entry. Those are separate G0-01 gaps.

DELETE FROM microservice_registry
WHERE service_name IN ('geihou-module-market', 'geihou-module-strategy');
