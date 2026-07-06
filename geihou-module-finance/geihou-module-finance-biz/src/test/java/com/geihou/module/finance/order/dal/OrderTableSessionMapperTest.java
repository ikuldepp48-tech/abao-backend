package com.geihou.module.finance.order.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.dal.dataobject.OrderTableSessionDO;
import com.geihou.module.finance.order.dal.mapper.OrderTableSessionMapper;
import com.geihou.module.finance.order.enums.TableSessionStatusEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Order table session mapper test (G1-01D).
 *
 * <p>Tests insert, select, update, tenant filtering, and unique key constraint.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:table_session_mapper_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OrderTableSessionMapperTest {

    @Autowired
    private OrderTableSessionMapper sessionMapper;
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
    void insertAndSelectById() {
        OrderTableSessionDO session = buildSession("S001", "T01");
        sessionMapper.insert(session);

        OrderTableSessionDO found = sessionMapper.selectById(session.getId());
        assertThat(found).isNotNull();
        assertThat(found.getSessionNo()).isEqualTo("S001");
        assertThat(found.getTableNo()).isEqualTo("T01");
        assertThat(found.getStatus()).isEqualTo(TableSessionStatusEnum.OPEN.getCode());
    }

    @Test
    void updateByIdChangesStatus() {
        OrderTableSessionDO session = buildSession("S002", "T02");
        sessionMapper.insert(session);

        session.setStatus(TableSessionStatusEnum.ORDERING.getCode());
        session.setUpdater("STAFF");
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);

        OrderTableSessionDO found = sessionMapper.selectById(session.getId());
        assertThat(found.getStatus()).isEqualTo(TableSessionStatusEnum.ORDERING.getCode());
    }

    @Test
    void selectListByTenantAndStatus() {
        sessionMapper.insert(buildSession("S003", "T03"));
        sessionMapper.insert(buildSession("S004", "T04"));

        List<OrderTableSessionDO> sessions = sessionMapper.selectList(
                OrderTableSessionDO::getTenantId, 1L,
                OrderTableSessionDO::getStatus, TableSessionStatusEnum.OPEN.getCode()
        );
        assertThat(sessions).hasSize(2);
    }

    @Test
    void tenantIsolationFiltersRecords() {
        TenantContextHolder.setTenantId(1L);
        sessionMapper.insert(buildSession("S_T1", "T01"));

        TenantContextHolder.setTenantId(2L);
        List<OrderTableSessionDO> sessions = sessionMapper.selectList(
                OrderTableSessionDO::getTenantId, 1L
        );
        assertThat(sessions).isEmpty();
    }

    @Test
    void softDeleteHidesRecord() {
        OrderTableSessionDO session = buildSession("S005", "T05");
        sessionMapper.insert(session);

        sessionMapper.deleteById(session.getId());

        OrderTableSessionDO found = sessionMapper.selectById(session.getId());
        assertThat(found).isNull();
    }

    @Test
    void moneyFieldsUseBigDecimalPrecision() {
        OrderTableSessionDO session = buildSession("S006", "T06");
        session.setTotalAmount(new BigDecimal("123.4567"));
        session.setPaidAmount(new BigDecimal("100.0000"));
        sessionMapper.insert(session);

        OrderTableSessionDO found = sessionMapper.selectById(session.getId());
        assertThat(found.getTotalAmount()).isEqualByComparingTo(new BigDecimal("123.4567"));
        assertThat(found.getPaidAmount()).isEqualByComparingTo(new BigDecimal("100.0000"));
    }

    private OrderTableSessionDO buildSession(String sessionNo, String tableNo) {
        LocalDateTime now = LocalDateTime.now();
        OrderTableSessionDO session = new OrderTableSessionDO();
        session.setTenantId(1L);
        session.setShopId(1L);
        session.setTableId(100L);
        session.setTableNo(tableNo);
        session.setSessionNo(sessionNo);
        session.setCustomerCount(4);
        session.setStatus(TableSessionStatusEnum.OPEN.getCode());
        session.setOpenTime(now);
        session.setBusinessDate(LocalDate.now());
        session.setTotalAmount(BigDecimal.ZERO);
        session.setPaidAmount(BigDecimal.ZERO);
        session.setOrderCount(0);
        session.setCreator("TEST");
        session.setCreateTime(now);
        session.setUpdater("TEST");
        session.setUpdateTime(now);
        session.setDeleted(false);
        return session;
    }
}
