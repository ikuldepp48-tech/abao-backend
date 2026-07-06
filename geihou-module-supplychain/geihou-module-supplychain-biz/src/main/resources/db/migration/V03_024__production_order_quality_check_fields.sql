-- V03_024__production_order_quality_check_fields.sql
-- G2-02M: production_order 质检/返工字段
-- Source: PRD-组2-02 §2.1, 02-全局枚举表-V2.md ENUM_PRODUCTION_STAGE
-- 实现: IN_PROGRESS → QUALITY_CHECK → COMPLETED / REWORK; REWORK → IN_PROGRESS
-- QUALITY_CHECK / REWORK 本身不写 stock_event; COMPLETED 来源允许 IN_PROGRESS 或 QUALITY_CHECK

ALTER TABLE production_order
  ADD COLUMN rework_count          INT          NOT NULL DEFAULT 0    COMMENT '返工次数(累计)',
  ADD COLUMN quality_check_result  VARCHAR(20)                       COMMENT '质检结果: PASS / FAIL',
  ADD COLUMN quality_check_remark  VARCHAR(500)                      COMMENT '质检备注/返工原因',
  ADD COLUMN quality_checked_by    BIGINT                            COMMENT '质检人ID',
  ADD COLUMN quality_checked_time  DATETIME                          COMMENT '质检时间';
