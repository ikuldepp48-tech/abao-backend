package com.geihou.module.finance.checkout;

import com.geihou.module.finance.cart.CartTestSchemaInitializer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
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
                        next_attempt_at DATETIME(3),
                        claim_token VARCHAR(64),
                        lease_until DATETIME(3),
                        last_error_code INT,
                        last_error_class VARCHAR(128),
                        last_error_message VARCHAR(512),
                        remote_executed_at DATETIME(3),
                        resolved_at DATETIME(3),
                        create_time DATETIME(3) NOT NULL,
                        update_time DATETIME(3) NOT NULL,
                        CONSTRAINT uk_fsc_remote_identity
                            UNIQUE (tenant_id, operation, business_command_id),
                        CONSTRAINT uk_fsc_local_step
                            UNIQUE (tenant_id, saga_type, saga_id, step_key, operation)
                    )
                    """);

            // G0-04H185 FIN-CONSISTENCY slice 2B: checkout cart item plan
            // Slice 2C-2B: six nullable columns (sku_code/stock_strategy/
            // bom_product_id/stock_item_id/location_id/classification_reason)
            // mirror V02_066 + V02_067 DDL. All six allow NULL; no defaults.
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS checkout_cart_item_plan (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        checkout_session_id BIGINT NOT NULL,
                        cart_item_id BIGINT NOT NULL,
                        sku_id BIGINT NOT NULL,
                        classification VARCHAR(16) NOT NULL,
                        sku_code VARCHAR(64),
                        stock_strategy VARCHAR(20),
                        bom_product_id BIGINT,
                        stock_item_id BIGINT,
                        location_id BIGINT,
                        classification_reason VARCHAR(32),
                        create_time DATETIME(3) NOT NULL,
                        update_time DATETIME(3) NOT NULL,
                        CONSTRAINT uk_ccip_tenant_session_cart
                            UNIQUE (tenant_id, checkout_session_id, cart_item_id)
                    )
                    """);

            // G0-04H185 FIN-CONSISTENCY slice 2C-2D: checkout saga intent.
            // Mirrors V02_068 (finalization_status defaults PENDING, all frozen
            // params NOT NULL, finalized_at nullable, DATETIME(3) timestamps).
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS finance_stock_saga_intent (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        saga_type VARCHAR(16) NOT NULL,
                        saga_id BIGINT NOT NULL,
                        cart_id BIGINT NOT NULL,
                        expected_checkout_status VARCHAR(20) NOT NULL,
                        target_checkout_status VARCHAR(20) NOT NULL,
                        cart_event_type VARCHAR(32) NOT NULL,
                        operator_user_id BIGINT NOT NULL,
                        operator_role VARCHAR(32) NOT NULL,
                        finalization_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                        finalized_at DATETIME(3) NULL,
                        create_time DATETIME(3) NOT NULL,
                        update_time DATETIME(3) NOT NULL,
                        CONSTRAINT uk_fsi_identity
                            UNIQUE (tenant_id, saga_type, saga_id)
                    )
                    """);

            // ── Index parity: bring H2 schema indexes in line with Flyway DDL ──
            // CartTestSchemaInitializer creates the cart/cart_item/cart_event_log/
            // checkout_session/checkout_idempotent tables WITHOUT indexes (column
            // parity only). finance_stock_command and checkout_cart_item_plan are
            // created above with inline UNIQUE constraints but no secondary indexes.
            // The block below adds every UNIQUE KEY and KEY declared in
            // V02_060..V02_066 so that CartCheckoutDdlConsistencyTest can assert
            // precise index parity (not just column parity).
            createIndexesIfMissing(stmt);

            // Clean all tables (preserve original cleanup behavior)
            stmt.execute("DELETE FROM finance_stock_saga_intent");
            stmt.execute("DELETE FROM checkout_cart_item_plan");
            stmt.execute("DELETE FROM finance_stock_command");
            stmt.execute("DELETE FROM checkout_idempotent");
            stmt.execute("DELETE FROM checkout_session");
            stmt.execute("DELETE FROM cart_event_log");
            stmt.execute("DELETE FROM cart_item");
            stmt.execute("DELETE FROM cart");
        }
    }

    /**
     * Create every UNIQUE KEY and KEY from V02_060..V02_066 DDL files.
     *
     * <p>Tables created by {@link CartTestSchemaInitializer} (cart, cart_item,
     * cart_event_log, checkout_session, checkout_idempotent) have no inline
     * constraints in H2, so both UNIQUE and non-UNIQUE indexes are added here.
     *
     * <p>{@code finance_stock_command} and {@code checkout_cart_item_plan}
     * already declare their UNIQUE constraints inline in H2 CREATE TABLE
     * above; only their secondary {@code KEY} indexes are added here. The
     * {@code IF NOT EXISTS} clause keeps this idempotent across re-runs and
     * safe if CartTestSchemaInitializer ever adds its own indexes.
     */
    private static void createIndexesIfMissing(Statement stmt) throws SQLException {
        // ── cart (V02_060) ──
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_active_cart " +
                "ON cart (tenant_id, customer_user_id, shop_id, status, deleted)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_tenant_status " +
                "ON cart (tenant_id, status, last_activity_time)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_table " +
                "ON cart (table_id, status)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_business_date " +
                "ON cart (tenant_id, business_date)");

        // ── cart_item (V02_061) ──
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_cart " +
                "ON cart_item (cart_id, deleted)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_sku " +
                "ON cart_item (sku_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_tenant_sku " +
                "ON cart_item (tenant_id, sku_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_promotion " +
                "ON cart_item (applied_promotion_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_item_state " +
                "ON cart_item (item_state)");

        // ── cart_event_log (V02_062) ──
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_cart_time " +
                "ON cart_event_log (cart_id, event_time)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_tenant_event " +
                "ON cart_event_log (tenant_id, event_type, event_time)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_operator " +
                "ON cart_event_log (operator_user_id, event_time)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_sku_time " +
                "ON cart_event_log (sku_id, event_time)");

        // ── checkout_session (V02_063) ──
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_session_token " +
                "ON checkout_session (session_token)");
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_cart_active " +
                "ON checkout_session (tenant_id, cart_id, status, deleted)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_tenant_customer " +
                "ON checkout_session (tenant_id, customer_user_id, status)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_tenant_shop_date " +
                "ON checkout_session (tenant_id, shop_id, business_date)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_expire_time " +
                "ON checkout_session (expire_time, status)");

        // ── checkout_idempotent (V02_064) ──
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_idempotent_key " +
                "ON checkout_idempotent (tenant_id, idempotent_key)");
        // H2 scopes index names to the schema (not per-table like MySQL), so
        // this cannot share the DDL name "idx_expire_time" with checkout_session.
        // The DDL-consistency test compares by (columns, unique), not by name.
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_ci_expire_time " +
                "ON checkout_idempotent (expire_time)");

        // ── finance_stock_command (V02_065) ──
        // UNIQUE constraints uk_fsc_remote_identity and uk_fsc_local_step are
        // declared inline in CREATE TABLE above; only secondary KEYs added here.
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_fsc_dispatch_scan " +
                "ON finance_stock_command (tenant_id, status, next_attempt_at)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_fsc_lease_scan " +
                "ON finance_stock_command (tenant_id, status, lease_until)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_fsc_saga " +
                "ON finance_stock_command (tenant_id, saga_type, saga_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_fsc_parent " +
                "ON finance_stock_command (tenant_id, parent_command_id)");

        // ── checkout_cart_item_plan (V02_066) ──
        // UNIQUE constraint uk_ccip_tenant_session_cart is declared inline in
        // CREATE TABLE above; only secondary KEY added here.
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_ccip_tenant_session " +
                "ON checkout_cart_item_plan (tenant_id, checkout_session_id)");

        // ── finance_stock_saga_intent (V02_068) ──
        // UNIQUE constraint uk_fsi_identity is declared inline in CREATE TABLE
        // above; only the secondary scan KEY is added here.
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_fsi_scan " +
                "ON finance_stock_saga_intent (tenant_id, finalization_status, update_time)");
    }
}
