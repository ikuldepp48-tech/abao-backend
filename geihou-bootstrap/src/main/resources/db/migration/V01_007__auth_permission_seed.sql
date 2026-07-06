-- Geihou Bootstrap runtime migration copy.
-- Runtime-applied by Flyway from geihou-bootstrap classpath resources.
-- Keep the body from the first INSERT byte-identical to the candidate copy.

INSERT INTO auth_permission (
  permission_code, permission_name, subsystem_id, module_name, resource, action, risk_level,
  description, creator, create_time, updater, update_time, deleted
) VALUES
('finance:core-profit:read', '核心利润计算引擎-读取', 1, 'finance-core-profit', 'core-profit', 'read', 'MEDIUM', '核心利润计算引擎读取权限', 'system', NOW(), 'system', NOW(), 0),
('cart:staff-assisted', '代客下单权限', 1, 'cart', 'cart', 'staff-assisted', 'MEDIUM', '员工代客下单权限(购物车辅助结算)', 'system', NOW(), 'system', NOW(), 0),
('ck:dashboard:read', '中央厨房仪表盘-读取', 2, 'ck-dashboard', 'dashboard', 'read', 'MEDIUM', '日产能 / 当日成本 / 配送进度 / 告警', 'system', NOW(), 'system', NOW(), 0),
('ck:procurement:read', '集中采购管理-读取', 2, 'ck-procurement', 'procurement', 'read', 'MEDIUM', '采购单 / 阶梯协议 / 比价 / 验收 / 付款', 'system', NOW(), 'system', NOW(), 0),
('ck:material:read', '原料档案+批次+保质期-读取', 2, 'ck-material', 'material', 'read', 'LOW', '原料 SKU / 批次追溯 / 保质期预警', 'system', NOW(), 'system', NOW(), 0),
('supplier:read', '供应商管理+8维度画像-读取', 2, 'supplier', 'supplier', 'read', 'LOW', '入口跳到组 4 实现的供应商画像', 'system', NOW(), 'system', NOW(), 0),
('bom:read', '半成品BOM配方-读取', 2, 'bom', 'bom', 'read', 'MEDIUM', '成品→半成品→原料层级 BOM / 损耗率', 'system', NOW(), 'system', NOW(), 0),
('production:write', '半成品制作登记-写入', 2, 'production', 'production', 'write', 'MEDIUM', '工单 / 原料投入 / 产出 / 损耗 / 工时', 'system', NOW(), 'system', NOW(), 0),
('cost:read', '中央厨房成本归集-读取', 1, 'cost', 'cost', 'read', 'MEDIUM', '双层归集:CK 内部 + 配送到门店', 'system', NOW(), 'system', NOW(), 0),
('employee:read', '中央厨房员工管理-读取', 3, 'employee', 'employee', 'read', 'MEDIUM', '员工双形态(C5)/ 排班 / 产能 / 提成', 'system', NOW(), 'system', NOW(), 0),
('delivery:write', '门店调拨配送-写入', 2, 'delivery', 'delivery', 'write', 'MEDIUM', '配送计划 / 出库 / 跟踪 / 门店收货 / 配送成本', 'system', NOW(), 'system', NOW(), 0),
('iot:read', 'CK IoT监控-读取', 6, 'iot', 'iot', 'read', 'LOW', '冰箱温度 / 烤箱 / 食安 / 能耗', 'system', NOW(), 'system', NOW(), 0),
('decision:read', 'CK决策管理-读取', 5, 'decision', 'decision', 'read', 'MEDIUM', '决策记录 / OKR / 任务下发(子系统 5)', 'system', NOW(), 'system', NOW(), 0),
('ck:settings:write', 'CK系统设置-写入', NULL, 'ck-settings', 'settings', 'write', 'HIGH', 'CK 基础配置 / 装修 / 模块开关', 'system', NOW(), 'system', NOW(), 0),
('consultant:dashboard:read', '咨询师仪表盘-读取', 9, 'consultant-dashboard', 'dashboard', 'read', 'MEDIUM', '我的客户 / 完整度排行 / 今日任务 / 告警', 'system', NOW(), 'system', NOW(), 0),
('consultant:client:read', '客户列表与档案-读取', NULL, 'consultant-client', 'client', 'read', 'MEDIUM', '客户列表与档案-读取(跨飞轮/洞察子系统)', 'system', NOW(), 'system', NOW(), 0),
('consultant:diagnosis:read', '5步生存链诊断-读取', 7, 'consultant-diagnosis', 'diagnosis', 'read', 'MEDIUM', '5 步生存链断点识别 + 改善建议', 'system', NOW(), 'system', NOW(), 0),
('consultant:strategy:write', '战略画像设计-写入', 5, 'consultant-strategy', 'strategy', 'write', 'MEDIUM', '5 因素 × 公共方法论 → 独特战略推导', 'system', NOW(), 'system', NOW(), 0),
('consultant:case:read', '案例库-读取', 9, 'consultant-case', 'case', 'read', 'LOW', '真实客户脱敏案例 + 检索 + 标签', 'system', NOW(), 'system', NOW(), 0),
('consultant:methodology:read', '方法论库-读取', 9, 'consultant-methodology', 'methodology', 'read', 'LOW', '41 份原始方法论 + 持续迭代', 'system', NOW(), 'system', NOW(), 0),
('consultant:benchmark:read', '行业基准对照-读取', 4, 'consultant-benchmark', 'benchmark', 'read', 'LOW', '跨租户脱敏聚合 / P25-P90 分位 / 同行业对照', 'system', NOW(), 'system', NOW(), 0);

INSERT INTO auth_role_permission (
  tenant_id, role_id, permission_id, creator, create_time, updater, update_time, deleted
)
SELECT 0, role_seed.id, permission_seed.id, 'system', NOW(), 'system', NOW(), 0
FROM (
  SELECT 'OWNER' AS role_code, 'finance:core-profit:read' AS permission_code
  UNION ALL SELECT 'OWNER', 'cart:staff-assisted'
  UNION ALL SELECT 'OWNER', 'ck:dashboard:read'
  UNION ALL SELECT 'OWNER', 'ck:procurement:read'
  UNION ALL SELECT 'OWNER', 'ck:material:read'
  UNION ALL SELECT 'OWNER', 'supplier:read'
  UNION ALL SELECT 'OWNER', 'bom:read'
  UNION ALL SELECT 'OWNER', 'production:write'
  UNION ALL SELECT 'OWNER', 'cost:read'
  UNION ALL SELECT 'OWNER', 'employee:read'
  UNION ALL SELECT 'OWNER', 'delivery:write'
  UNION ALL SELECT 'OWNER', 'iot:read'
  UNION ALL SELECT 'OWNER', 'decision:read'
  UNION ALL SELECT 'OWNER', 'ck:settings:write'
  UNION ALL SELECT 'SHOP_MANAGER', 'cart:staff-assisted'
  UNION ALL SELECT 'CK_MANAGER', 'ck:dashboard:read'
  UNION ALL SELECT 'CK_MANAGER', 'ck:procurement:read'
  UNION ALL SELECT 'CK_MANAGER', 'ck:material:read'
  UNION ALL SELECT 'CK_MANAGER', 'supplier:read'
  UNION ALL SELECT 'CK_MANAGER', 'bom:read'
  UNION ALL SELECT 'CK_MANAGER', 'production:write'
  UNION ALL SELECT 'CK_MANAGER', 'cost:read'
  UNION ALL SELECT 'CK_MANAGER', 'employee:read'
  UNION ALL SELECT 'CK_MANAGER', 'delivery:write'
  UNION ALL SELECT 'CK_MANAGER', 'iot:read'
  UNION ALL SELECT 'CK_MANAGER', 'decision:read'
  UNION ALL SELECT 'CK_MANAGER', 'ck:settings:write'
  UNION ALL SELECT 'CASHIER', 'cart:staff-assisted'
  UNION ALL SELECT 'CK_WORKER', 'ck:dashboard:read'
  UNION ALL SELECT 'CK_WORKER', 'ck:material:read'
  UNION ALL SELECT 'CK_WORKER', 'bom:read'
  UNION ALL SELECT 'CK_WORKER', 'production:write'
  UNION ALL SELECT 'CONSULTANT', 'finance:core-profit:read'
  UNION ALL SELECT 'CONSULTANT', 'consultant:dashboard:read'
  UNION ALL SELECT 'CONSULTANT', 'consultant:client:read'
  UNION ALL SELECT 'CONSULTANT', 'consultant:diagnosis:read'
  UNION ALL SELECT 'CONSULTANT', 'consultant:strategy:write'
  UNION ALL SELECT 'CONSULTANT', 'consultant:case:read'
  UNION ALL SELECT 'CONSULTANT', 'consultant:methodology:read'
  UNION ALL SELECT 'CONSULTANT', 'consultant:benchmark:read'
) AS grant_seed
JOIN auth_role AS role_seed
  ON role_seed.role_code = grant_seed.role_code
 AND role_seed.tenant_id = 0
 AND role_seed.is_builtin = 1
 AND role_seed.status = 'ACTIVE'
 AND role_seed.deleted = 0
JOIN auth_permission AS permission_seed
  ON permission_seed.permission_code = grant_seed.permission_code
 AND permission_seed.deleted = 0;
