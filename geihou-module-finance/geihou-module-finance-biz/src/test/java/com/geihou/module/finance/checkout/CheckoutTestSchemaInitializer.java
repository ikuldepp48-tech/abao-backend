package com.geihou.module.finance.checkout;

import com.geihou.module.finance.cart.CartTestSchemaInitializer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Helper to initialize H2 schema for checkout tests.
 *
 * <p>Delegates cart table creation (cart, cart_item, cart_event_log) to
 * {@link CartTestSchemaInitializer}, then creates checkout-specific tables.
 * Preserves cleanup behavior for all tables.
 */
public final class CheckoutTestSchemaInitializer {

    private CheckoutTestSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws Exception {
        // Delegate cart tables (and checkout tables, since CartTestSchemaInitializer
        // also creates them) creation and cleanup to CartTestSchemaInitializer.
        CartTestSchemaInitializer.initialize(dataSource);

        // Create checkout-specific tables (IF NOT EXISTS makes this safe even if
        // already created by CartTestSchemaInitializer).
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {

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

            // Clean all tables (preserve original cleanup behavior)
            stmt.execute("DELETE FROM checkout_idempotent");
            stmt.execute("DELETE FROM checkout_session");
            stmt.execute("DELETE FROM cart_event_log");
            stmt.execute("DELETE FROM cart_item");
            stmt.execute("DELETE FROM cart");
        }
    }
}
