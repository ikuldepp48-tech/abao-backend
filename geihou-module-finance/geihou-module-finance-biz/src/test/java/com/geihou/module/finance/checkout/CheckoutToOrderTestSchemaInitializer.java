package com.geihou.module.finance.checkout;

import javax.sql.DataSource;

/**
 * Combined schema initializer for G1-04C checkout-to-order conversion tests.
 *
 * <p>Delegates to {@link CheckoutTestSchemaInitializer} (cart + checkout tables)
 * and {@link com.geihou.module.finance.order.OrderTestSchemaInitializer} (order tables)
 * to create all tables needed for the full conversion flow.
 */
public final class CheckoutToOrderTestSchemaInitializer {

    private CheckoutToOrderTestSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws Exception {
        // Creates cart, cart_item, cart_event_log, checkout_session, checkout_idempotent
        // and cleans them.
        CheckoutTestSchemaInitializer.initialize(dataSource);
        // Creates orders, order_items, order_event_log, order_idempotent, order_payment,
        // order_refund, order_table_session and cleans them.
        com.geihou.module.finance.order.OrderTestSchemaInitializer.initialize(dataSource);
    }
}
