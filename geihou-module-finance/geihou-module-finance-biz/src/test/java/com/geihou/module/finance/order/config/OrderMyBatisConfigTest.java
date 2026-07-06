package com.geihou.module.finance.order.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.service.OrderService;
import com.geihou.common.pojo.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Order MyBatis config test.
 *
 * <p>Verifies that OptimisticLockerInnerInterceptor is registered,
 * and that PaginationInnerInterceptor remains active (not replaced).
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:order_mybatis_config_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OrderMyBatisConfigTest {

    @Autowired
    private MybatisPlusInterceptor mybatisPlusInterceptor;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        OrderTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void optimisticLockerInterceptorIsRegistered() {
        boolean hasOptimisticLocker = mybatisPlusInterceptor.getInterceptors().stream()
                .anyMatch(i -> i instanceof OptimisticLockerInnerInterceptor);
        assertThat(hasOptimisticLocker).isTrue();
    }

    @Test
    void paginationInterceptorStillRegistered() {
        boolean hasPagination = mybatisPlusInterceptor.getInterceptors().stream()
                .anyMatch(i -> i instanceof PaginationInnerInterceptor);
        assertThat(hasPagination).isTrue();
    }

    @Test
    void paginationQueryStillWorks() {
        // Create multiple orders
        for (int i = 0; i < 5; i++) {
            createOrder();
        }

        // Page query should work with PaginationInnerInterceptor
        PageResult<OrderDO> page = orderMapper.selectPage(1, 3);
        assertThat(page).isNotNull();
        assertThat(page.getList()).hasSize(3);
        assertThat(page.getTotal()).isEqualTo(5);
    }

    @Test
    void optimisticLockVersionIncrementsOnUpdate() {
        Long orderId = createOrder();
        OrderDO order = orderMapper.selectById(orderId);
        int initialVersion = order.getVersion();

        order.setStatus("PAID");
        orderMapper.updateById(order);

        OrderDO updated = orderMapper.selectById(orderId);
        assertThat(updated.getVersion()).isEqualTo(initialVersion + 1);
    }

    private Long createOrder() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item1 = new OrderItemReqVO();
        item1.setSkuId(1001L);
        item1.setQuantity(new BigDecimal("1"));

        req.setItems(List.of(item1));
        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());
        return resp.getOrderId();
    }
}
