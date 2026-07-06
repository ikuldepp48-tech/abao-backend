package com.geihou.module.finance.cart.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.cart.CartTestConfig;
import com.geihou.module.finance.cart.CartTestSchemaInitializer;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartVO;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;
import com.geihou.module.finance.cart.dal.mapper.CartItemMapper;
import com.geihou.module.finance.cart.service.CartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cart snapshot test (AC-9).
 *
 * <p>Verifies that sku_name_snapshot and unit_price_snapshot are frozen
 * at add time and cannot be modified by subsequent operations.
 */
@SpringBootTest(
        classes = CartTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:cart_snapshot_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CartSnapshotTest {

    @Autowired
    private CartService cartService;
    @Autowired
    private CartItemMapper cartItemMapper;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        CartTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void snapshotFieldsFrozenAtAddTime() {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        CartVO vo = cartService.addItem(7001L, 1L, "DINE_IN", req);

        List<CartItemDO> items = cartItemMapper.selectList();
        assertThat(items).hasSize(1);

        CartItemDO item = items.get(0);
        // Verify snapshot fields
        assertThat(item.getSkuNameSnapshot()).isEqualTo("Beef Burger");
        assertThat(item.getUnitPriceSnapshot()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(item.getSkuImageSnapshot()).isEqualTo("https://example.com/beef.jpg");
    }

    @Test
    void snapshotUnchangedAfterQuantityUpdate() {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        CartVO vo = cartService.addItem(7002L, 1L, "DINE_IN", req);
        Long itemId = vo.getItems().get(0).getId();

        // Update quantity — snapshot should remain unchanged
        cartService.updateQuantity(7002L, 1L, itemId, 5);

        CartItemDO item = cartItemMapper.selectById(itemId);
        assertThat(item.getSkuNameSnapshot()).isEqualTo("Beef Burger");
        assertThat(item.getUnitPriceSnapshot()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(item.getQuantity()).isEqualTo(5);
    }
}
