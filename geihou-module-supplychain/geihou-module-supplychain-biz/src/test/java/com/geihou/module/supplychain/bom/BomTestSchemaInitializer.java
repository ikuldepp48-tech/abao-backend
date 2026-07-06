package com.geihou.module.supplychain.bom;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Helper to initialize H2 schema for BOM tests.
 *
 * <p>Creates H2-compatible versions of product_master, bom_recipe, bom_recipe_item tables.
 *
 * <p>Source: TASK-G2-02A.
 */
public final class BomTestSchemaInitializer {

    private BomTestSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {

            // product_master table
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS product_master (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        product_code VARCHAR(64) NOT NULL,
                        product_name VARCHAR(128) NOT NULL,
                        product_type VARCHAR(32) NOT NULL,
                        sku_code VARCHAR(64),
                        unit VARCHAR(16) NOT NULL,
                        category VARCHAR(64),
                        description VARCHAR(512),
                        is_active BOOLEAN NOT NULL DEFAULT TRUE,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE,
                        CONSTRAINT uk_tenant_product_code UNIQUE (tenant_id, product_code, deleted)
                    )
                    """);

            // bom_recipe table (G2-02B: includes output_quantity / output_unit)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS bom_recipe (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        product_id BIGINT NOT NULL,
                        version_no INT NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        remark VARCHAR(512),
                        output_quantity DECIMAL(18,6) NOT NULL DEFAULT 1,
                        output_unit VARCHAR(16) NOT NULL DEFAULT '',
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE,
                        CONSTRAINT uk_tenant_product_version UNIQUE (tenant_id, product_id, version_no, deleted)
                    )
                    """);

            // bom_recipe_item table (G2-02B: includes component_type / unit)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS bom_recipe_item (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        recipe_id BIGINT NOT NULL,
                        component_product_id BIGINT NOT NULL,
                        quantity DECIMAL(18,6) NOT NULL,
                        waste_rate DECIMAL(10,6) NOT NULL DEFAULT 0,
                        component_type VARCHAR(32) NOT NULL DEFAULT 'RAW_MATERIAL',
                        unit VARCHAR(16) NOT NULL DEFAULT '',
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            // Clean tables (order: items first, then recipes, then products)
            stmt.execute("DELETE FROM bom_recipe_item");
            stmt.execute("DELETE FROM bom_recipe");
            stmt.execute("DELETE FROM product_master");
        }
    }
}
