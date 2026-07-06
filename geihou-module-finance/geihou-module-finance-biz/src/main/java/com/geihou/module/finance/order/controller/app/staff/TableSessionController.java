package com.geihou.module.finance.order.controller.app.staff;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.finance.order.controller.app.staff.vo.TableSessionOpenReqVO;
import com.geihou.module.finance.order.controller.app.staff.vo.TableSessionRespVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderTableSessionDO;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.service.tablesession.TableSessionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Staff table session controller (G1-01D slice).
 *
 * <p>Endpoints for staff to manage table session lifecycle:
 * <ul>
 *   <li>POST /app-api/staff/order/table-session/open — Open a table session</li>
 *   <li>GET /app-api/staff/order/table-session/{sessionNo} — Get session detail with orders</li>
 *   <li>POST /app-api/staff/order/table-session/{sessionNo}/settle — Settle session</li>
 *   <li>POST /app-api/staff/order/table-session/{sessionNo}/close — Close session</li>
 * </ul>
 */
@RestController
@RequestMapping("/app-api/staff/order/table-session")
public class TableSessionController {

    private final TableSessionService tableSessionService;

    public TableSessionController(TableSessionService tableSessionService) {
        this.tableSessionService = tableSessionService;
    }

    /**
     * Open a new table session (staff operation).
     */
    @PostMapping("/open")
    public CommonResult<TableSessionRespVO> openSession(@RequestBody TableSessionOpenReqVO reqVO) {
        try {
            if (reqVO.getShopId() == null || reqVO.getTableId() == null ||
                reqVO.getTableNo() == null || reqVO.getTableNo().isBlank()) {
                return CommonResult.error(400, "shopId, tableId, tableNo are required");
            }
            OrderTableSessionDO session = tableSessionService.openSession(
                    reqVO.getShopId(), reqVO.getTableId(), reqVO.getTableNo(), reqVO.getCustomerCount());
            return CommonResult.success(toRespVO(session, List.of()));
        } catch (OrderBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Get session detail by session_no, including associated orders.
     */
    @GetMapping("/{sessionNo}")
    public CommonResult<TableSessionRespVO> getSession(@PathVariable String sessionNo) {
        try {
            OrderTableSessionDO session = tableSessionService.getBySessionNo(sessionNo);
            if (session == null) {
                return CommonResult.error(404, "Table session not found: " + sessionNo);
            }
            List<OrderDO> orders = tableSessionService.getSessionOrders(session.getId());
            return CommonResult.success(toRespVO(session, orders));
        } catch (OrderBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Settle a session: ORDERING/SERVING → SETTLING, aggregate amounts.
     */
    @PostMapping("/{sessionNo}/settle")
    public CommonResult<TableSessionRespVO> settleSession(@PathVariable String sessionNo) {
        try {
            OrderTableSessionDO session = tableSessionService.settleSession(sessionNo);
            List<OrderDO> orders = tableSessionService.getSessionOrders(session.getId());
            return CommonResult.success(toRespVO(session, orders));
        } catch (OrderBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Close a session: SETTLING → CLOSED, set close_time.
     */
    @PostMapping("/{sessionNo}/close")
    public CommonResult<TableSessionRespVO> closeSession(@PathVariable String sessionNo) {
        try {
            OrderTableSessionDO session = tableSessionService.closeSession(sessionNo);
            List<OrderDO> orders = tableSessionService.getSessionOrders(session.getId());
            return CommonResult.success(toRespVO(session, orders));
        } catch (OrderBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    private TableSessionRespVO toRespVO(OrderTableSessionDO session, List<OrderDO> orders) {
        TableSessionRespVO resp = new TableSessionRespVO();
        resp.setId(session.getId());
        resp.setSessionNo(session.getSessionNo());
        resp.setShopId(session.getShopId());
        resp.setTableId(session.getTableId());
        resp.setTableNo(session.getTableNo());
        resp.setCustomerCount(session.getCustomerCount());
        resp.setStatus(session.getStatus());
        resp.setOpenTime(session.getOpenTime());
        resp.setSettleTime(session.getSettleTime());
        resp.setCloseTime(session.getCloseTime());
        resp.setBusinessDate(session.getBusinessDate());
        resp.setTotalAmount(session.getTotalAmount());
        resp.setPaidAmount(session.getPaidAmount());
        resp.setOrderCount(session.getOrderCount());
        if (orders != null) {
            List<TableSessionRespVO.OrderSummary> summaries = orders.stream().map(order -> {
                TableSessionRespVO.OrderSummary summary = new TableSessionRespVO.OrderSummary();
                summary.setOrderId(order.getId());
                summary.setOrderNo(order.getOrderNo());
                summary.setStatus(order.getStatus());
                summary.setTotalAmount(order.getTotalAmount());
                summary.setPaidAmount(order.getPaidAmount());
                summary.setCreateTime(order.getCreateTime());
                return summary;
            }).collect(Collectors.toList());
            resp.setOrders(summaries);
        }
        return resp;
    }
}
