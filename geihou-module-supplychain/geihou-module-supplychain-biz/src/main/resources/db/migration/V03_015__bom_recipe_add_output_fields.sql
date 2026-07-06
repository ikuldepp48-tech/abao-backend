-- V03_015__bom_recipe_add_output_fields.sql
-- G2-02B: Augment bom_recipe with output_quantity / output_unit for proportional explosion.

ALTER TABLE bom_recipe
  ADD COLUMN output_quantity DECIMAL(18,6) NOT NULL DEFAULT 1 COMMENT '本配方产出量',
  ADD COLUMN output_unit     VARCHAR(16)   NOT NULL DEFAULT '' COMMENT '产出单位';
