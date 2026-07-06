-- V03_016__bom_recipe_item_add_component_fields.sql
-- G2-02B: Augment bom_recipe_item with component_type / unit for explosion aggregation.

ALTER TABLE bom_recipe_item
  ADD COLUMN component_type VARCHAR(32) NOT NULL DEFAULT 'RAW_MATERIAL' COMMENT '子项类型(FINISHED/SEMI_FINISHED/RAW_MATERIAL)',
  ADD COLUMN unit           VARCHAR(16) NOT NULL DEFAULT '' COMMENT '子项单位';
