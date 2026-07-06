-- G2-01A: stock_event table (INSERT-only,不可篡改)
-- Source: TASK-G2-01A Section 4.1
-- CG-12-A: event_type uses global enum table 11 values
-- CG-12-B: no RESERVE_OUT/RESERVE_RELEASE/RESERVE_COMMIT

CREATE TABLE stock_event (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID',
  event_time      DATETIME     NOT NULL                  COMMENT '事件发生时间',
  business_date   DATE         NOT NULL                  COMMENT '业务日期(跨日切分)',

  -- 事件核心
  event_type      VARCHAR(32)  NOT NULL                  COMMENT 'ENUM_STOCK_EVENT_TYPE（全局枚举表 11 值）',
  direction       VARCHAR(20)  NOT NULL                  COMMENT 'IN(入)/ OUT(出)/ INTERNAL(内部)',

  -- 库存项 + 库位
  stock_item_id   BIGINT       NOT NULL                  COMMENT '库存品项 ID',
  sku_code        VARCHAR(64)  NOT NULL                  COMMENT 'SKU(冗余,加速查询)',
  location_id     BIGINT       NOT NULL                  COMMENT '库位 ID',

  -- 数量(BigDecimal,禁 double)
  quantity        DECIMAL(18,4) NOT NULL                 COMMENT '数量(永远为正,direction 决定增减)',
  unit            VARCHAR(16)  NOT NULL                  COMMENT '单位 kg/g/个/箱',

  -- 单价 + 金额(本切片可为 null)
  unit_cost       DECIMAL(18,4)                          COMMENT '单位成本(加权移动平均,G2-01B 实现)',
  total_cost      DECIMAL(18,4)                          COMMENT '总成本',

  -- 来源 / 关联
  source_module   VARCHAR(64)                            COMMENT '来源模块(order / production / count / waste)',
  source_record_id BIGINT                                COMMENT '来源记录 ID',
  reference_no    VARCHAR(64)                            COMMENT '业务单号',

  -- 幂等
  client_request_id VARCHAR(64)                          COMMENT '客户端请求 ID(幂等)',

  -- 操作人
  operator_user_id BIGINT      NOT NULL                  COMMENT '操作人',

  -- 余额快照(冗余)
  balance_after   DECIMAL(18,4)                          COMMENT '本事件后余额(冗余)',

  -- 仅 INSERT(不可篡改)
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',

  -- 索引
  KEY idx_tenant_item_time (tenant_id, stock_item_id, event_time),
  KEY idx_tenant_location_time (tenant_id, location_id, event_time),
  KEY idx_tenant_business_date (tenant_id, business_date, event_type),
  KEY idx_source (source_module, source_record_id),
  UNIQUE KEY uk_tenant_client_request (tenant_id, client_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存事件流水(不可篡改,几好数据真实性最底层)';
