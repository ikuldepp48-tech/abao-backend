-- Geihou Bootstrap runtime migration copy.
-- Source candidate: /Users/mac/Desktop/abao-projects/abao-backend/db/migrations/V01_003__init_abao_tenant.sql
-- Header fixed before first Flyway migrate. Do not edit this migration after it is applied;
-- create a later migration version for any future schema or seed change.
-- Geihou is the platform; Abao is tenant sample #1 only.

-- ============================================================
-- 初始化 14 个微服务注册(11 业务 + 2 公共 + 1 网关)
-- OWNER: 组 0 - 基础设施
-- ============================================================
INSERT INTO microservice_registry (service_name, service_port, subsystem_id, service_type, health_check_url, startup_priority, is_required, creator, create_time, updater, update_time) VALUES
('geihou-module-gateway',         48080, NULL, 'GATEWAY',  '/actuator/health',  1,  1, 'system', NOW(), 'system', NOW()),
('geihou-module-finance',         48081, 1,    'BUSINESS', '/actuator/health',  20, 1, 'system', NOW(), 'system', NOW()),
('geihou-module-supplychain',     48082, 2,    'BUSINESS', '/actuator/health',  20, 1, 'system', NOW(), 'system', NOW()),
('geihou-module-hr',              48083, 3,    'BUSINESS', '/actuator/health',  20, 1, 'system', NOW(), 'system', NOW()),
('geihou-module-market',          48084, 4,    'BUSINESS', '/actuator/health',  20, 1, 'system', NOW(), 'system', NOW()),
('geihou-module-strategy',        48085, 5,    'BUSINESS', '/actuator/health',  20, 1, 'system', NOW(), 'system', NOW()),
('geihou-module-risk',            48086, 6,    'BUSINESS', '/actuator/health',  20, 1, 'system', NOW(), 'system', NOW()),
('geihou-module-gene',            48087, 7,    'BUSINESS', '/actuator/health',  20, 1, 'system', NOW(), 'system', NOW()),
('geihou-module-insight',         48088, 8,    'BUSINESS', '/actuator/health',  20, 1, 'system', NOW(), 'system', NOW()),
('geihou-module-flywheel',        48089, 9,    'BUSINESS', '/actuator/health',  20, 1, 'system', NOW(), 'system', NOW()),
('geihou-module-strategy-track',  48090, 10,   'BUSINESS', '/actuator/health',  20, 1, 'system', NOW(), 'system', NOW()),
('geihou-module-knowledge',       48091, 11,   'BUSINESS', '/actuator/health',  20, 1, 'system', NOW(), 'system', NOW()),
('geihou-module-system',          48092, NULL, 'COMMON',   '/actuator/health',  10, 1, 'system', NOW(), 'system', NOW()),
('geihou-module-infra',           48093, NULL, 'COMMON',   '/actuator/health',  10, 1, 'system', NOW(), 'system', NOW());

-- ============================================================
-- 初始化 1 号租户:阿堡(abao) — 几好平台的第 1 个客户
-- OWNER: 组 0 - 基础设施
-- ============================================================
INSERT INTO tenants (tenant_code, tenant_name, merchant_type, status, life_stage, business_day_cutoff_hour, timezone, creator, create_time, updater, update_time) VALUES
('abao', '阿堡(几好平台 1 号客户)', 'B', 'ACTIVE', 'EXPLORATION', 3, 'Asia/Shanghai', 'system', NOW(), 'system', NOW());

-- ============================================================
-- 为 1 号租户启用所有 11 子系统(M1 阶段全开,M2-M6 按节奏)
-- OWNER: 组 0 - 基础设施
-- ============================================================
INSERT INTO tenant_subsystem_enabled (tenant_id, subsystem_id, enabled, enable_time, creator, create_time, updater, update_time)
SELECT 1, n, 1, NOW(), 'system', NOW(), 'system', NOW()
FROM (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11) AS t;
