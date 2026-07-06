package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.StockBalanceRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StockBalanceService}.
 *
 * <p>Covers: AC-2 (balance not raw-changed — read-only service).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_balance_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockBalanceServiceTest {

    @Autowired
    private StockBalanceService stockBalanceService;
    @Autowired
    private StockEventService stockEventService;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void getAvailableQty_noBalanceReturnsZero() {
        BigDecimal available = stockBalanceService.getAvailableQty(1L, 9999L, 99L);
        assertThat(available).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getAvailableQty_afterPurchaseReturnsCorrectAmount() {
        stockEventService.recordEvent(buildInReq(new BigDecimal("100")));

        BigDecimal available = stockBalanceService.getAvailableQty(1L, 1001L, 10L);
        assertThat(available).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void checkAvailable_sufficientReturnsTrue() {
        stockEventService.recordEvent(buildInReq(new BigDecimal("50")));

        boolean result = stockBalanceService.checkAvailable(1L, 1001L, 10L, new BigDecimal("30"));
        assertThat(result).isTrue();
    }

    @Test
    void checkAvailable_insufficientReturnsFalse() {
        stockEventService.recordEvent(buildInReq(new BigDecimal("20")));

        boolean result = stockBalanceService.checkAvailable(1L, 1001L, 10L, new BigDecimal("50"));
        assertThat(result).isFalse();
    }

    @Test
    void checkAvailable_exactAmountReturnsTrue() {
        stockEventService.recordEvent(buildInReq(new BigDecimal("50")));

        boolean result = stockBalanceService.checkAvailable(1L, 1001L, 10L, new BigDecimal("50"));
        assertThat(result).isTrue();
    }

    @Test
    void getBalance_returnsFullDto() {
        stockEventService.recordEvent(buildInReq(new BigDecimal("75")));

        StockBalanceRespDTO dto = stockBalanceService.getBalance(1L, 1001L, 10L);
        assertThat(dto).isNotNull();
        assertThat(dto.getAvailableQty()).isEqualByComparingTo(new BigDecimal("75"));
        assertThat(dto.getTotalQty()).isEqualByComparingTo(new BigDecimal("75"));
        assertThat(dto.getTenantId()).isEqualTo(1L);
        assertThat(dto.getStockItemId()).isEqualTo(1001L);
        assertThat(dto.getLocationId()).isEqualTo(10L);
    }

    @Test
    void getBalance_noBalanceReturnsNull() {
        StockBalanceRespDTO dto = stockBalanceService.getBalance(1L, 9999L, 99L);
        assertThat(dto).isNull();
    }

    private StockEventReqDTO buildInReq(BigDecimal quantity) {
        StockEventReqDTO req = new StockEventReqDTO();
        req.setTenantId(1L);
        req.setEventTime(LocalDateTime.now());
        req.setBusinessDate(LocalDate.now());
        req.setEventType(StockEventTypeEnum.PURCHASE_IN.getCode());
        req.setDirection(StockDirectionEnum.IN.getCode());
        req.setStockItemId(1001L);
        req.setSkuCode("SKU_TEST");
        req.setLocationId(10L);
        req.setQuantity(quantity);
        req.setUnit("个");
        req.setOperatorUserId(999L);
        req.setClientRequestId("req-" + UUID.randomUUID());
        return req;
    }
}
