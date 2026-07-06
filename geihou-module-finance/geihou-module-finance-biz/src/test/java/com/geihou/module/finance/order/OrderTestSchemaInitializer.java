package com.geihou.module.finance.order;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Helper to initialize H2 schema for order tests.
 *
 * <p>Creates H2-compatible versions of the order tables (MySQL DDL features like
 * BIT/UNSIGNED are adapted for H2 MySQL mode).
 */
public final class OrderTestSchemaInitializer {

    private OrderTestSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS orders (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        order_no VARCHAR(32) NOT NULL,
                        business_date DATE NOT NULL,
                        channel VARCHAR(32) NOT NULL,
                        order_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
                        customer_user_id BIGINT,
                        customer_phone VARCHAR(32),
                        customer_name VARCHAR(64),
                        member_id BIGINT,
                        member_level VARCHAR(20),
                        shop_id BIGINT NOT NULL,
                        table_session_id BIGINT,
                        table_no VARCHAR(32),
                        total_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        paid_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        discount_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        refund_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        platform_fee DECIMAL(18,4) NOT NULL DEFAULT 0,
                        status VARCHAR(32) NOT NULL,
                        payment_method VARCHAR(32),
                        pay_time DATETIME,
                        kitchen_time DATETIME,
                        ready_time DATETIME,
                        delivered_time DATETIME,
                        completed_time DATETIME,
                        cancelled_time DATETIME,
                        cancel_reason VARCHAR(255),
                        promotion_ids VARCHAR(255),
                        coupon_id BIGINT,
                        customer_remark VARCHAR(500),
                        internal_remark VARCHAR(500),
                        version INT NOT NULL DEFAULT 0,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS order_items (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        order_id BIGINT NOT NULL,
                        sku_id BIGINT NOT NULL,
                        sku_code VARCHAR(64) NOT NULL,
                        sku_name VARCHAR(128) NOT NULL,
                        spu_id BIGINT NOT NULL,
                        spu_name VARCHAR(128) NOT NULL,
                        category_id BIGINT NOT NULL,
                        unit_price DECIMAL(18,4) NOT NULL,
                        quantity DECIMAL(18,4) NOT NULL,
                        unit VARCHAR(16) NOT NULL DEFAULT '份',
                        item_discount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        item_total DECIMAL(18,4) NOT NULL,
                        item_paid DECIMAL(18,4) NOT NULL,
                        modifiers CLOB,
                        refunded_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
                        refunded_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        item_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS order_event_log (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        order_id BIGINT NOT NULL,
                        event_type VARCHAR(32) NOT NULL,
                        before_status VARCHAR(32),
                        after_status VARCHAR(32),
                        operator_user_id BIGINT,
                        operator_role VARCHAR(32),
                        payload CLOB,
                        client_ip VARCHAR(64),
                        event_time DATETIME NOT NULL,
                        create_time DATETIME NOT NULL
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS order_idempotent (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        idempotent_key VARCHAR(64) NOT NULL,
                        order_id BIGINT,
                        status VARCHAR(20) NOT NULL,
                        expire_time DATETIME NOT NULL,
                        create_time DATETIME NOT NULL,
                        CONSTRAINT uk_tenant_idempotent_key UNIQUE (tenant_id, idempotent_key)
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS order_payment (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        order_id BIGINT NOT NULL,
                        payment_no VARCHAR(64) NOT NULL,
                        external_no VARCHAR(128),
                        payment_method VARCHAR(32) NOT NULL,
                        payment_amount DECIMAL(18,4) NOT NULL,
                        payment_status VARCHAR(20) NOT NULL,
                        initiated_time DATETIME NOT NULL,
                        paid_time DATETIME,
                        failed_time DATETIME,
                        failed_reason VARCHAR(255),
                        create_time DATETIME NOT NULL,
                        CONSTRAINT uk_tenant_payment_no UNIQUE (tenant_id, payment_no)
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS order_refund (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        refund_no VARCHAR(32) NOT NULL,
                        original_order_id BIGINT NOT NULL,
                        original_payment_id BIGINT NOT NULL,
                        refund_amount DECIMAL(18,4) NOT NULL,
                        refund_type VARCHAR(20) NOT NULL,
                        refund_item_ids VARCHAR(1024),
                        reason_type VARCHAR(32) NOT NULL,
                        reason_detail VARCHAR(500) NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        approver_user_id BIGINT,
                        approve_time DATETIME,
                        approve_remark VARCHAR(500),
                        refund_time DATETIME,
                        external_refund_no VARCHAR(128),
                        fail_reason VARCHAR(500),
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            // Create order_table_session table (G1-01D)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS order_table_session (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        shop_id BIGINT NOT NULL,
                        table_id BIGINT NOT NULL,
                        table_no VARCHAR(32) NOT NULL,
                        session_no VARCHAR(32) NOT NULL,
                        customer_count INT,
                        status VARCHAR(20) NOT NULL,
                        open_time DATETIME NOT NULL,
                        settle_time DATETIME,
                        close_time DATETIME,
                        business_date DATE NOT NULL,
                        total_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        paid_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        order_count INT NOT NULL DEFAULT 0,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE,
                        CONSTRAINT uk_tenant_session_no UNIQUE (tenant_id, session_no, deleted)
                    )
                    """);

            // Clean all tables
            stmt.execute("DELETE FROM order_refund");
            stmt.execute("DELETE FROM order_payment");
            stmt.execute("DELETE FROM order_idempotent");
            stmt.execute("DELETE FROM order_event_log");
            stmt.execute("DELETE FROM order_items");
            stmt.execute("DELETE FROM orders");
            stmt.execute("DELETE FROM order_table_session");
        }
    }
}
