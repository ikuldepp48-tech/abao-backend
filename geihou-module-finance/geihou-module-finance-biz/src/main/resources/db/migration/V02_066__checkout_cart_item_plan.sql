-- ============================================================
-- TABLE: checkout_cart_item_plan
-- OWNER: G0-04H185 FIN-CONSISTENCY (checkout classification plan slice 2B)
-- 子系统: 经营 ① 钱进来(7 大经营动作)
-- 多租户: 必含 tenant_id
-- 软删除: 无 (无 deleted 列, 无 @TableLogic)
-- 外键: 无
--
-- 本表是 checkout cart item 的耐久、只写一次分类计划。
-- 每个 (checkout_session, cart_item) 准确获得一行计划, 分类准确为
-- BOM / NON_BOM / UNMAPPED, 后续阶段必须读取计划, 不能重新推导。
--
-- 切片 2B 只建立 DDL/DO/Mapper/Store 和测试。不接 CheckoutServiceImpl,
-- 不调用 supplychain, 不创建 RESERVE 命令。
--
-- 冻结契约:
-- - 不可变身份: (tenant_id, checkout_session_id, cart_item_id)
-- - 不可变字段: sku_id, classification (创建后永不更新)
-- - classification ∈ {BOM, NON_BOM, UNMAPPED}
-- - 无 update 路径: Store 不暴露 update* 方法
--
-- 参考:
-- - G0-04H185-FIN-CONSISTENCY-SAGA-PROTOCOL-FREEZE.md (协议冻结稿)
-- - TASK-G0-04H185-FIN-CONSISTENCY-CHECKOUT-CLASSIFICATION-PLAN-FOUNDATION.md (切片 2B 任务)
-- ============================================================
CREATE TABLE checkout_cart_item_plan (
  id                        BIGINT UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id                 BIGINT           NOT NULL                  COMMENT '租户ID',
  checkout_session_id       BIGINT           NOT NULL                  COMMENT 'checkout_session.id',
  cart_item_id              BIGINT           NOT NULL                  COMMENT 'cart_item.id',
  sku_id                    BIGINT           NOT NULL                  COMMENT 'SKU ID (创建时快照, 不可变)',
  classification            VARCHAR(16)      NOT NULL                  COMMENT 'CheckoutStockClassification: BOM / NON_BOM / UNMAPPED',

  -- 时间戳 (无 creator/updater/deleted)
  create_time               DATETIME(3)      NOT NULL                  COMMENT '创建时间',
  update_time               DATETIME(3)      NOT NULL                  COMMENT '更新时间',

  -- 索引
  UNIQUE KEY uk_ccip_tenant_session_cart
    (tenant_id, checkout_session_id, cart_item_id),
  KEY idx_ccip_tenant_session
    (tenant_id, checkout_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='checkout cart item 分类计划表 (只写一次)';
