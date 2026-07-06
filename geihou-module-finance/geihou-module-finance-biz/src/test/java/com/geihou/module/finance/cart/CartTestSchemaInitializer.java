package com.geihou.module.finance.cart;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Helper to initialize H2 schema for cart tests.
 *
 * <p>Creates H2-compatible versions of the cart tables (MySQL DDL features like
 * BIT/UNSIGNED/JSON are adapted for H2 MySQL mode).
 */
public final class CartTestSchemaInitializer {

    private CartTestSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS cart (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        customer_user_id BIGINT NOT NULL,
                        shop_id BIGINT NOT NULL,
                        table_id BIGINT,
                        channel VARCHAR(32) NOT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                        item_count INT NOT NULL DEFAULT 0,
                        total_quantity INT NOT NULL DEFAULT 0,
                        subtotal_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        discount_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        total_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        business_date DATE,
                        is_staff_assisted BOOLEAN NOT NULL DEFAULT FALSE,
                        assisted_by_user_id BIGINT,
                        version INT NOT NULL DEFAULT 0,
                        last_activity_time DATETIME NOT NULL,
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS cart_item (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        cart_id BIGINT NOT NULL,
                        sku_id BIGINT NOT NULL,
                        spu_id BIGINT NOT NULL,
                        sku_name_snapshot VARCHAR(128) NOT NULL,
                        sku_image_snapshot VARCHAR(512),
                        unit_price_snapshot DECIMAL(18,4) NOT NULL,
                        quantity INT NOT NULL,
                        options CLOB,
                        options_extra_price DECIMAL(18,4) NOT NULL DEFAULT 0,
                        item_subtotal DECIMAL(18,4) NOT NULL,
                        item_discount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        item_total DECIMAL(18,4) NOT NULL,
                        applied_promotion_id BIGINT,
                        item_state VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS cart_event_log (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        cart_id BIGINT NOT NULL,
                        event_type VARCHAR(32) NOT NULL,
                        event_time DATETIME NOT NULL,
                        operator_user_id BIGINT NOT NULL,
                        operator_role VARCHAR(32) NOT NULL,
                        sku_id BIGINT,
                        quantity_before INT,
                        quantity_after INT,
                        amount_before DECIMAL(18,4),
                        amount_after DECIMAL(18,4),
                        extra CLOB,
                        client_ip VARCHAR(64),
                        user_agent VARCHAR(512),
                        device_id VARCHAR(64),
                        create_time DATETIME NOT NULL
                    )
                    """);

            // Checkout tables (G1-04B)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS checkout_session (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        cart_id BIGINT NOT NULL,
                        customer_user_id BIGINT NOT NULL,
                        shop_id BIGINT NOT NULL,
                        session_token VARCHAR(64) NOT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
                        subtotal_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        discount_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        locked_discount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        total_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
                        applied_promotions CLOB,
                        applied_coupon_ids VARCHAR(512),
                        payment_method VARCHAR(32),
                        payment_time DATETIME,
                        payment_trade_no VARCHAR(64),
                        order_id BIGINT,
                        business_date DATE,
                        expire_time DATETIME NOT NULL,
                        channel VARCHAR(32) NOT NULL,
                        remark VARCHAR(512),
                        creator VARCHAR(64) NOT NULL DEFAULT '',
                        create_time DATETIME NOT NULL,
                        updater VARCHAR(64) NOT NULL DEFAULT '',
                        update_time DATETIME NOT NULL,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS checkout_idempotent (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        idempotent_key VARCHAR(64) NOT NULL,
                        session_id BIGINT,
                        session_token VARCHAR(64),
                        status VARCHAR(20) NOT NULL,
                        expire_time DATETIME NOT NULL,
                        create_time DATETIME NOT NULL
                    )
                    """);

            // Clean all tables
            stmt.execute("DELETE FROM checkout_idempotent");
            stmt.execute("DELETE FROM checkout_session");
            stmt.execute("DELETE FROM cart_event_log");
            stmt.execute("DELETE FROM cart_item");
            stmt.execute("DELETE FROM cart");
        }
    }
}
