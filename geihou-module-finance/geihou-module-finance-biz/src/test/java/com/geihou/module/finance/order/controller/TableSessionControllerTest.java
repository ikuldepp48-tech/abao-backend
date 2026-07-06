package com.geihou.module.finance.order.controller;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.app.staff.TableSessionController;
import com.geihou.module.finance.order.controller.app.staff.vo.TableSessionOpenReqVO;
import com.geihou.module.finance.order.controller.app.staff.vo.TableSessionRespVO;
import com.geihou.module.finance.order.dal.dataobject.OrderTableSessionDO;
import com.geihou.module.finance.order.enums.TableSessionStatusEnum;
import com.geihou.module.finance.order.service.tablesession.TableSessionService;
import com.geihou.common.pojo.CommonResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Table session controller test (G1-01D).
 *
 * <p>Tests open, get, settle, close endpoints, and parameter validation.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:table_session_controller_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class TableSessionControllerTest {

    @Autowired
    private TableSessionController tableSessionController;
    @Autowired
    private TableSessionService tableSessionService;
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
    void openSessionReturnsSessionWithStatusOpen() {
        TableSessionOpenReqVO req = buildOpenReq(1L, 101L, "T01", 4);

        CommonResult<TableSessionRespVO> result = tableSessionController.openSession(req);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getStatus()).isEqualTo(TableSessionStatusEnum.OPEN.getCode());
        assertThat(result.getData().getSessionNo()).isNotNull().isNotEmpty();
        assertThat(result.getData().getTableNo()).isEqualTo("T01");
    }

    @Test
    void openSessionRejectsMissingShopId() {
        TableSessionOpenReqVO req = buildOpenReq(null, 101L, "T01", 4);

        CommonResult<TableSessionRespVO> result = tableSessionController.openSession(req);

        assertThat(result.getCode()).isNotEqualTo(0);
    }

    @Test
    void openSessionRejectsMissingTableNo() {
        TableSessionOpenReqVO req = buildOpenReq(1L, 101L, null, 4);

        CommonResult<TableSessionRespVO> result = tableSessionController.openSession(req);

        assertThat(result.getCode()).isNotEqualTo(0);
    }

    @Test
    void getSessionReturnsSessionDetail() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);

        CommonResult<TableSessionRespVO> result = tableSessionController.getSession(session.getSessionNo());

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getId()).isEqualTo(session.getId());
        assertThat(result.getData().getTableNo()).isEqualTo("T01");
    }

    @Test
    void getSessionReturns404ForNonExistent() {
        CommonResult<TableSessionRespVO> result = tableSessionController.getSession("NONEXISTENT");

        assertThat(result.getCode()).isEqualTo(404);
    }

    @Test
    void settleSessionTransitionsToSettling() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        tableSessionService.validateForDineIn(session.getId()); // OPEN→ORDERING

        CommonResult<TableSessionRespVO> result = tableSessionController.settleSession(session.getSessionNo());

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getStatus()).isEqualTo(TableSessionStatusEnum.SETTLING.getCode());
        assertThat(result.getData().getSettleTime()).isNotNull();
    }

    @Test
    void closeSessionTransitionsToClosed() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        tableSessionService.validateForDineIn(session.getId());
        tableSessionService.settleSession(session.getSessionNo());

        CommonResult<TableSessionRespVO> result = tableSessionController.closeSession(session.getSessionNo());

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getStatus()).isEqualTo(TableSessionStatusEnum.CLOSED.getCode());
        assertThat(result.getData().getCloseTime()).isNotNull();
    }

    @Test
    void closeSessionRejectsAlreadyClosed() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        tableSessionService.validateForDineIn(session.getId());
        tableSessionService.settleSession(session.getSessionNo());
        tableSessionService.closeSession(session.getSessionNo());

        CommonResult<TableSessionRespVO> result = tableSessionController.closeSession(session.getSessionNo());

        assertThat(result.getCode()).isNotEqualTo(0);
    }

    private TableSessionOpenReqVO buildOpenReq(Long shopId, Long tableId, String tableNo, Integer customerCount) {
        TableSessionOpenReqVO req = new TableSessionOpenReqVO();
        req.setShopId(shopId);
        req.setTableId(tableId);
        req.setTableNo(tableNo);
        req.setCustomerCount(customerCount);
        return req;
    }
}
