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

            // G0-04H185 FIN-CONSISTENCY slice 1: durable command store
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS finance_stock_command (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        saga_type VARCHAR(16) NOT NULL,
                        saga_id BIGINT NOT NULL,
                        step_key VARCHAR(128) NOT NULL,
                        parent_command_id BIGINT,
                        operation VARCHAR(32) NOT NULL,
                        business_command_id VARCHAR(128) NOT NULL,
                        transport_mode VARCHAR(16) NOT NULL,
                        c0_journal_available BOOLEAN NOT NULL,
                        request_schema_version INT NOT NULL DEFAULT 1,
                        request_body BLOB NOT NULL,
                        request_body_sha256 VARCHAR(64) NOT NULL,
                        result_schema_version INT,
                        result_body BLOB,
                        status VARCHAR(20) NOT NULL,
                        abort_requested BOOLEAN NOT NULL DEFAULT FALSE,
                        dispatch_attempts INT NOT NULL DEFAULT 0,
                        resolution_attempts INT NOT NULL DEFAULT 0,
                        max_dispatch_attempts INT NOT NULL,
                        max_resolution_attempts INT NOT NULL,
                        next_attempt_at DATETIME,
                        claim_token VARCHAR(64),
                        lease_until DATETIME,
                        last_error_code INT,
                        last_error_class VARCHAR(128),
                        last_error_message VARCHAR(512),
                        remote_executed_at DATETIME,
                        resolved_at DATETIME,
                        create_time DATETIME NOT NULL,
                        update_time DATETIME NOT NULL,
                        CONSTRAINT uk_fsc_remote_identity
                            UNIQUE (tenant_id, operation, business_command_id),
                        CONSTRAINT uk_fsc_local_step
                            UNIQUE (tenant_id, saga_type, saga_id, step_key, operation)
                    )
                    """);

            // Clean all tables (preserve original cleanup behavior)
            stmt.execute("DELETE FROM finance_stock_command");
            stmt.execute("DELETE FROM checkout_idempotent");
            stmt.execute("DELETE FROM checkout_session");
            stmt.execute("DELETE FROM cart_event_log");
            stmt.execute("DELETE FROM cart_item");
            stmt.execute("DELETE FROM cart");
        }
    }
}
