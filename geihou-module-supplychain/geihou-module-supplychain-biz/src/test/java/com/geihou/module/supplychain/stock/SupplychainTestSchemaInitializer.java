package com.geihou.module.supplychain.stock;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Helper to initialize H2 schema for supplychain stock tests.
 *
 * <p>Creates H2-compatible versions of stock_event and stock_balance tables.
 * MySQL DDL features (ENGINE, CHARSET, UNSIGNED, BIT) are adapted for H2 MySQL mode.
 */
public final class SupplychainTestSchemaInitializer {

    private SupplychainTestSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {

            // stock_event table (INSERT-only, no update_time/updater/deleted)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS stock_event (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        event_time DATETIME NOT NULL,
                        business_date DATE NOT NULL,
                        event_type VARCHAR(32) NOT NULL,
                        direction VARCHAR(20) NOT NULL,
                        stock_item_id BIGINT NOT NULL,
                        sku_code VARCHAR(64) NOT NULL,
                        location_id BIGINT NOT NULL,
                        quantity DECIMAL(18,4) NOT NULL,
                        unit VARCHAR(16) NOT NULL,
                        unit_cost DECIMAL(18,4),
                        total_cost DECIMAL(18,4),
                        source_module VARCHAR(64),
                        source_record_id BIGINT,
                        source_order_item_id BIGINT,
                        reference_no VARCHAR(64),
                        client_request_id VARCHAR(191),
                        operator_user_id BIGINT NOT NULL,
                        balance_after DECIMAL(18,4),
                        create_time DATETIME NOT NULL,
                        parent_event_id BIGINT,
                        recipe_id BIGINT,
                        recipe_version INT,
                        adjustment_reason VARCHAR(512),
                        adjustment_sign TINYINT,
                        CONSTRAINT uk_tenant_client_request UNIQUE (tenant_id, client_request_id)
                    )
                    """);

            // Index for parent event lookup (tenant-scoped)
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_stock_event_tenant_parent " +
                    "ON stock_event (tenant_id, parent_event_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_stock_event_tenant_source_line " +
                    "ON stock_event (tenant_id, source_module, source_record_id, source_order_item_id, event_type)");

            // stock_balance table (with optimistic lock version, G2-01B1: added reserved_qty)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS stock_balance (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        stock_item_id BIGINT NOT NULL,
                        location_id BIGINT NOT NULL,
                        available_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
                        total_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
                        reserved_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
                        avg_unit_cost DECIMAL(18,4) NOT NULL DEFAULT 0,
                        last_event_id BIGINT,
                        last_event_time DATETIME,
                        version INT NOT NULL DEFAULT 0,
                        min_threshold DECIMAL(18,4),
                        max_threshold DECIMAL(18,4),
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            // stock_location table (G2-01B1, G2-01B3A: added uk_tenant_store_type)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS stock_location (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        location_code VARCHAR(64) NOT NULL,
                        location_name VARCHAR(128) NOT NULL,
                        location_type VARCHAR(32) NOT NULL,
                        store_id BIGINT,
                        is_active BOOLEAN NOT NULL DEFAULT TRUE,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE,
                        CONSTRAINT uk_tenant_code UNIQUE (tenant_id, location_code, deleted),
                        CONSTRAINT uk_tenant_store_type UNIQUE (tenant_id, store_id, location_type, deleted)
                    )
                    """);

            // stock_item table (G2-01B1, G2-01B3A: added is_active)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS stock_item (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        sku_code VARCHAR(64) NOT NULL,
                        item_name VARCHAR(128) NOT NULL,
                        category VARCHAR(64),
                        unit VARCHAR(16) NOT NULL,
                        shelf_life_days INT,
                        storage_condition VARCHAR(64),
                        is_raw_material BOOLEAN NOT NULL DEFAULT FALSE,
                        is_semi_finished BOOLEAN NOT NULL DEFAULT FALSE,
                        is_finished BOOLEAN NOT NULL DEFAULT FALSE,
                        is_active BOOLEAN NOT NULL DEFAULT TRUE,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE,
                        CONSTRAINT uk_tenant_sku UNIQUE (tenant_id, sku_code, deleted)
                    )
                    """);

            // stock_reserve table (G2-01B1)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS stock_reserve (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        stock_item_id BIGINT NOT NULL,
                        location_id BIGINT NOT NULL,
                        sku_code VARCHAR(64) NOT NULL,
                        quantity DECIMAL(18,4) NOT NULL,
                        unit VARCHAR(16) NOT NULL,
                        source_module VARCHAR(64) NOT NULL,
                        source_record_id BIGINT NOT NULL,
                        reference_no VARCHAR(64),
                        idempotent_key VARCHAR(128) NOT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
                        commit_event_id BIGINT,
                        operator_user_id BIGINT NOT NULL,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE,
                        CONSTRAINT uk_tenant_idempotent UNIQUE (tenant_id, idempotent_key, deleted)
                    )
                    """);

            // stock_mapping_coverage_audit table (G2-01B3B)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS stock_mapping_coverage_audit (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        coverage_type VARCHAR(32) NOT NULL,
                        sku_id BIGINT,
                        sku_code VARCHAR(64),
                        store_id BIGINT,
                        location_type VARCHAR(32),
                        source_module VARCHAR(64) NOT NULL,
                        source_record_id BIGINT NOT NULL,
                        idempotent_key VARCHAR(128) NOT NULL,
                        mode VARCHAR(20) NOT NULL,
                        first_seen_time DATETIME NOT NULL,
                        last_seen_time DATETIME NOT NULL,
                        seen_count INT NOT NULL DEFAULT 1,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE,
                        CONSTRAINT uk_tenant_coverage_source UNIQUE (
                            tenant_id, coverage_type, source_module, source_record_id, idempotent_key, deleted
                        )
                    )
                    """);

            // product_master table (BOM — needed by StockCheckServiceImpl via ProductMasterMapper)
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
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            // bom_recipe table (BOM — needed by BomExplosionServiceImpl via BomRecipeMapper)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS bom_recipe (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        product_id BIGINT NOT NULL,
                        version_no INT,
                        status VARCHAR(32) NOT NULL,
                        remark VARCHAR(256),
                        output_quantity DECIMAL(18,4),
                        output_unit VARCHAR(16),
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            // bom_recipe_item table (BOM — needed by BomExplosionServiceImpl via BomRecipeItemMapper)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS bom_recipe_item (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        recipe_id BIGINT NOT NULL,
                        component_product_id BIGINT NOT NULL,
                        quantity DECIMAL(18,4) NOT NULL,
                        waste_rate DECIMAL(18,4),
                        component_type VARCHAR(32),
                        unit VARCHAR(16),
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            // stock_loss table (G2-02I-3)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS stock_loss (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        loss_no VARCHAR(64) NOT NULL,
                        loss_type VARCHAR(16) NOT NULL,
                        stock_item_id BIGINT NOT NULL,
                        sku_code VARCHAR(64) NOT NULL,
                        location_id BIGINT NOT NULL,
                        quantity DECIMAL(18,4) NOT NULL,
                        unit VARCHAR(16) NOT NULL,
                        unit_cost DECIMAL(18,4),
                        total_amount DECIMAL(18,4),
                        loss_reason VARCHAR(30) NOT NULL,
                        remark VARCHAR(500),
                        status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVE',
                        approver_user_id BIGINT,
                        approve_time DATETIME,
                        reject_reason VARCHAR(500),
                        stock_event_id BIGINT,
                        operator_user_id BIGINT NOT NULL,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE,
                        CONSTRAINT uk_tenant_loss_no UNIQUE (tenant_id, loss_no, deleted)
                    )
                    """);

            // stock_count_session table (G2-02I-2)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS stock_count_session (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        session_code VARCHAR(64) NOT NULL,
                        location_id BIGINT NOT NULL,
                        count_type VARCHAR(20) NOT NULL,
                        scheduled_time DATETIME,
                        start_time DATETIME,
                        end_time DATETIME,
                        status VARCHAR(20) NOT NULL DEFAULT 'PLANNING',
                        total_items INT,
                        diff_items INT,
                        total_diff_value DECIMAL(18,4),
                        operator_user_id BIGINT,
                        approver_user_id BIGINT,
                        approve_time DATETIME,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE,
                        CONSTRAINT uk_tenant_session UNIQUE (tenant_id, session_code, deleted)
                    )
                    """);

            // stock_count_record table (G2-02I-2, INSERT-only: no deleted/updater/update_time/creator)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS stock_count_record (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        session_id BIGINT NOT NULL,
                        stock_item_id BIGINT NOT NULL,
                        system_qty DECIMAL(18,4) NOT NULL,
                        actual_qty DECIMAL(18,4) NOT NULL,
                        diff_qty DECIMAL(18,4) NOT NULL,
                        diff_reason VARCHAR(512),
                        evidence_url VARCHAR(512),
                        adjustment_event_id BIGINT,
                        create_time DATETIME NOT NULL
                    )
                    """);

            // production_order table (G2-02K, G2-02M: added quality check / rework fields)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS production_order (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        order_no VARCHAR(64) NOT NULL,
                        product_id BIGINT NOT NULL,
                        recipe_id BIGINT NOT NULL,
                        location_id BIGINT NOT NULL,
                        planned_qty DECIMAL(18,4) NOT NULL,
                        actual_qty DECIMAL(18,4),
                        production_stage VARCHAR(20) NOT NULL DEFAULT 'CREATED',
                        plan_start_time DATETIME,
                        plan_end_time DATETIME,
                        actual_start_time DATETIME,
                        actual_end_time DATETIME,
                        operator_user_id BIGINT,
                        remark VARCHAR(500),
                        rework_count INT NOT NULL DEFAULT 0,
                        quality_check_result VARCHAR(20),
                        quality_check_remark VARCHAR(500),
                        quality_checked_by BIGINT,
                        quality_checked_time DATETIME,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE,
                        CONSTRAINT uk_tenant_order_no UNIQUE (tenant_id, order_no, deleted)
                    )
                    """);

            // G2-02M: Idempotent ALTER TABLE for pre-existing production_order tables
            // (ensures new quality check columns exist even if table was created by earlier schema init)
            try { stmt.execute("ALTER TABLE production_order ADD COLUMN rework_count INT NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE production_order ADD COLUMN quality_check_result VARCHAR(20)"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE production_order ADD COLUMN quality_check_remark VARCHAR(500)"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE production_order ADD COLUMN quality_checked_by BIGINT"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE production_order ADD COLUMN quality_checked_time DATETIME"); } catch (Exception ignored) {}

            // G2-02N: production_order_consumption table (INSERT-only, 领料记录)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS production_order_consumption (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        production_order_id BIGINT NOT NULL,
                        input_sku_id BIGINT NOT NULL,
                        pick_seq INT NOT NULL,
                        planned_qty DECIMAL(18,4),
                        actual_qty DECIMAL(18,4) NOT NULL,
                        diff_qty DECIMAL(18,4),
                        diff_reason VARCHAR(500),
                        stock_event_id BIGINT,
                        unit_cost DECIMAL(18,4),
                        total_cost DECIMAL(18,4),
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE,
                        CONSTRAINT uk_tenant_order_component_seq UNIQUE (
                            tenant_id, production_order_id, input_sku_id, pick_seq, deleted
                        )
                    )
                    """);

            // G2-02N: production_order_output table (INSERT-only, 产出登记)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS production_order_output (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        production_order_id BIGINT NOT NULL,
                        output_sku_id BIGINT NOT NULL,
                        output_seq INT NOT NULL,
                        actual_output_qty DECIMAL(18,4) NOT NULL,
                        output_quality_grade VARCHAR(10),
                        stock_event_id BIGINT,
                        unit_cost DECIMAL(18,4),
                        total_cost DECIMAL(18,4),
                        batch_no VARCHAR(64),
                        produced_time DATETIME,
                        expire_time DATETIME,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE,
                        CONSTRAINT uk_tenant_order_output_seq UNIQUE (
                            tenant_id, production_order_id, output_seq, deleted
                        )
                    )
                    """);

            // transfer_order table (G2-02S)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS transfer_order (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        transfer_no VARCHAR(64) NOT NULL,
                        from_location_id BIGINT NOT NULL,
                        to_location_id BIGINT NOT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                        created_by BIGINT NOT NULL,
                        shipped_by BIGINT,
                        received_by BIGINT,
                        cancelled_by BIGINT,
                        shipped_at DATETIME,
                        received_at DATETIME,
                        cancelled_at DATETIME,
                        remark VARCHAR(500),
                        cancel_reason VARCHAR(500),
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE,
                        CONSTRAINT uk_tenant_transfer_no UNIQUE (tenant_id, transfer_no, deleted)
                    )
                    """);

            // transfer_order_item table (G2-02S)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS transfer_order_item (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        transfer_order_id BIGINT NOT NULL,
                        product_id BIGINT NOT NULL,
                        stock_item_id BIGINT NOT NULL,
                        sku_code VARCHAR(64) NOT NULL,
                        quantity DECIMAL(18,4) NOT NULL,
                        unit VARCHAR(16) NOT NULL,
                        out_event_id BIGINT,
                        in_event_id BIGINT,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            // supplychain_command_journal table (C0/FIN-CONSISTENCY)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS supplychain_command_journal (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        operation VARCHAR(32) NOT NULL,
                        business_command_id VARCHAR(128) NOT NULL,
                        request_body_sha256 CHAR(64) NOT NULL,
                        result_schema_version INT NOT NULL DEFAULT 1,
                        result_snapshot TEXT NOT NULL,
                        executed_at TIMESTAMP(3) NOT NULL,
                        create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT uk_tenant_op_cmd UNIQUE (tenant_id, operation, business_command_id)
                    )
                    """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_scj_tenant_executed " +
                    "ON supplychain_command_journal (tenant_id, executed_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_scj_tenant_op_executed " +
                    "ON supplychain_command_journal (tenant_id, operation, executed_at)");

            // Clean tables (order matters: items before transfer_order, consumption/output before production_order)
            stmt.execute("DELETE FROM supplychain_command_journal");
            stmt.execute("DELETE FROM transfer_order_item");
            stmt.execute("DELETE FROM transfer_order");
            stmt.execute("DELETE FROM production_order_consumption");
            stmt.execute("DELETE FROM production_order_output");
            stmt.execute("DELETE FROM stock_count_record");
            stmt.execute("DELETE FROM stock_count_session");
            stmt.execute("DELETE FROM stock_loss");
            stmt.execute("DELETE FROM stock_reserve");
            stmt.execute("DELETE FROM stock_mapping_coverage_audit");
            stmt.execute("DELETE FROM stock_event");
            stmt.execute("DELETE FROM stock_balance");
            stmt.execute("DELETE FROM stock_location");
            stmt.execute("DELETE FROM stock_item");
            stmt.execute("DELETE FROM bom_recipe_item");
            stmt.execute("DELETE FROM bom_recipe");
            stmt.execute("DELETE FROM product_master");
            stmt.execute("DELETE FROM production_order");
        }
    }
}
