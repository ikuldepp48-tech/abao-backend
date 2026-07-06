-- G2-01A: stock_balance table (由事件计算,不可裸改)
-- Source: TASK-G2-01A Section 4.2

CREATE TABLE stock_balance (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID',
  stock_item_id   BIGINT       NOT NULL                  COMMENT '库存项',
  location_id     BIGINT       NOT NULL                  COMMENT '库位',

  -- 余额
  available_qty   DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '可用数量',
  total_qty       DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '总库存',

  -- 加权移动平均成本(G2-01B 实现,本切片可为 0)
  avg_unit_cost   DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '加权移动平均单位成本',

  -- 元数据
  last_event_id   BIGINT                                 COMMENT '最后一笔事件 ID(乐观锁)',
  last_event_time DATETIME                               COMMENT '最后事件时间',
  version         INT          NOT NULL DEFAULT 0        COMMENT '乐观锁版本号',

  -- 阈值告警(G2-01B 实现,本切片字段预留)
  min_threshold   DECIMAL(18,4)                          COMMENT '最低库存预警阈值',
  max_threshold   DECIMAL(18,4)                          COMMENT '最高库存预警阈值',

  creator         VARCHAR(64) NOT NULL DEFAULT ''
  ,create_time    DATETIME NOT NULL
  ,updater        VARCHAR(64) NOT NULL DEFAULT ''
  ,update_time    DATETIME NOT NULL
  ,deleted        BIT(1) NOT NULL DEFAULT 0
  ,UNIQUE KEY uk_tenant_item_location (tenant_id, stock_item_id, location_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存余额(由事件计算)';
