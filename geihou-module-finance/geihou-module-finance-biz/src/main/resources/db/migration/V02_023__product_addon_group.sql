-- ============================================================
-- TABLE: product_addon_group
-- 加料分组(如"加料 - 奶油 / 糖")。租户级实体,无 spu_id 字段。
-- Source: PRD-G1-02 节 2.2 DDL / G1-02F 任务包节 6.1
-- Note: 软删除 deleted BIT(1); 联合唯一键 uk_tenant_group 含 tenant_id。
-- ============================================================
CREATE TABLE product_addon_group (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL                  COMMENT '租户ID',

  group_code          VARCHAR(64)  NOT NULL                  COMMENT '加料组编码',
  group_name          VARCHAR(128) NOT NULL                  COMMENT '加料组名(如"加料 - 配料")',
  select_min          INT          NOT NULL DEFAULT 0        COMMENT '最少选几个(0 = 可不选)',
  select_max          INT          NOT NULL                  COMMENT '最多选几个',
  is_required         BIT(1)       NOT NULL DEFAULT 0        COMMENT '是否必选',
  sort_order          INT          NOT NULL DEFAULT 0        COMMENT '排序',

  -- 审计字段
  creator             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time         DATETIME     NOT NULL                  COMMENT '创建时间',
  updater             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time         DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted             BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',

  UNIQUE KEY uk_tenant_group (tenant_id, group_code, deleted),
  KEY idx_tenant_status (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加料分组';
