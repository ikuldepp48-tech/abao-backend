package com.geihou.module.finance.product;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Helper to initialize H2 schema for product tests.
 *
 * <p>Creates H2-compatible versions of the product tables (MySQL DDL features like
 * JSON/BIT/UNSIGNED are adapted for H2 MySQL mode).
 */
public final class TestSchemaInitializer {

    private TestSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS product_category (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        category_code VARCHAR(64) NOT NULL,
                        category_name VARCHAR(128) NOT NULL,
                        parent_category_id BIGINT,
                        category_path VARCHAR(512),
                        level TINYINT NOT NULL,
                        icon VARCHAR(255),
                        sort_order INT NOT NULL DEFAULT 0,
                        status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS product_spu (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        spu_code VARCHAR(64) NOT NULL,
                        spu_name VARCHAR(128) NOT NULL,
                        spu_short_name VARCHAR(64),
                        category_id BIGINT NOT NULL,
                        spu_type VARCHAR(32) NOT NULL,
                        primary_image_url VARCHAR(512),
                        image_gallery CLOB,
                        description CLOB,
                        is_recommended BOOLEAN NOT NULL DEFAULT FALSE,
                        is_new_arrival BOOLEAN NOT NULL DEFAULT FALSE,
                        sort_order INT NOT NULL DEFAULT 0,
                        total_sold_count INT NOT NULL DEFAULT 0,
                        status VARCHAR(20) NOT NULL DEFAULT 'NEW',
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS product_sku (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        spu_id BIGINT NOT NULL,
                        sku_code VARCHAR(64) NOT NULL,
                        sku_name VARCHAR(128) NOT NULL,
                        spec_attributes CLOB,
                        list_price DECIMAL(18,4) NOT NULL,
                        selling_price DECIMAL(18,4) NOT NULL,
                        cost_price DECIMAL(18,4),
                        member_price DECIMAL(18,4),
                        daily_limit INT,
                        per_order_limit INT,
                        min_order_quantity INT NOT NULL DEFAULT 1,
                        stock_strategy VARCHAR(20) NOT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'NEW',
                        status_reason VARCHAR(255),
                        primary_image_url VARCHAR(512),
                        total_sold_count INT NOT NULL DEFAULT 0,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS product_price_history (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        sku_id BIGINT NOT NULL,
                        old_list_price DECIMAL(18,4),
                        new_list_price DECIMAL(18,4) NOT NULL,
                        old_selling_price DECIMAL(18,4),
                        new_selling_price DECIMAL(18,4) NOT NULL,
                        change_reason VARCHAR(255) NOT NULL,
                        change_type VARCHAR(32) NOT NULL,
                        changed_by_user_id BIGINT NOT NULL,
                        change_time DATETIME NOT NULL,
                        create_time DATETIME NOT NULL
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS product_availability_log (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        target_type VARCHAR(10) NOT NULL,
                        target_id BIGINT NOT NULL,
                        old_status VARCHAR(20),
                        new_status VARCHAR(20) NOT NULL,
                        change_reason VARCHAR(255) NOT NULL,
                        changed_by_user_id BIGINT NOT NULL,
                        change_time DATETIME NOT NULL,
                        create_time DATETIME NOT NULL
                    )
                    """);

            // G1-02F tables: addon_group, addon_option, combo, combo_item
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS product_addon_group (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        group_code VARCHAR(64) NOT NULL,
                        group_name VARCHAR(128) NOT NULL,
                        select_min INT NOT NULL DEFAULT 0,
                        select_max INT NOT NULL,
                        is_required BOOLEAN NOT NULL DEFAULT FALSE,
                        sort_order INT NOT NULL DEFAULT 0,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS product_addon_option (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        addon_group_id BIGINT NOT NULL,
                        option_sku_id BIGINT NOT NULL,
                        option_name VARCHAR(128) NOT NULL,
                        extra_price DECIMAL(18,4) NOT NULL DEFAULT 0,
                        sort_order INT NOT NULL DEFAULT 0,
                        status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS product_combo (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        combo_sku_id BIGINT NOT NULL,
                        combo_name VARCHAR(128) NOT NULL,
                        combo_price DECIMAL(18,4) NOT NULL,
                        effective_from DATETIME,
                        effective_until DATETIME,
                        status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS product_combo_item (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        combo_id BIGINT NOT NULL,
                        item_sku_id BIGINT NOT NULL,
                        quantity INT NOT NULL,
                        is_optional BOOLEAN NOT NULL DEFAULT FALSE,
                        alternative_group INT,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            // G1-02G table: product_spu_addon_group mapping
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS product_spu_addon_group (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        spu_id BIGINT NOT NULL,
                        addon_group_id BIGINT NOT NULL,
                        sort_order INT NOT NULL DEFAULT 0,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            // Clean all tables (order matters for FK-like relationships)
            stmt.execute("DELETE FROM product_spu_addon_group");
            stmt.execute("DELETE FROM product_combo_item");
            stmt.execute("DELETE FROM product_combo");
            stmt.execute("DELETE FROM product_addon_option");
            stmt.execute("DELETE FROM product_addon_group");
            stmt.execute("DELETE FROM product_availability_log");
            stmt.execute("DELETE FROM product_price_history");
            stmt.execute("DELETE FROM product_sku");
            stmt.execute("DELETE FROM product_spu");
            stmt.execute("DELETE FROM product_category");
        }
    }
}
