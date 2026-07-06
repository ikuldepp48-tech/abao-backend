-- V03_010__stock_location_add_uk_store_type.sql
-- G2-01B3A: Add unique key on (tenant_id, store_id, location_type, deleted)
-- Ensures mapping uniqueness for store_id + location_type within a tenant.

ALTER TABLE stock_location
  ADD UNIQUE KEY uk_tenant_store_type (tenant_id, store_id, location_type, deleted);
