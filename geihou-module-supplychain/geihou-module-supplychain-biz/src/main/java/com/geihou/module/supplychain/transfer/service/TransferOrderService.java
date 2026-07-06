package com.geihou.module.supplychain.transfer.service;

import com.geihou.common.pojo.PageResult;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderCreateReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderShipReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderReceiveReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderCancelReqVO;
import com.geihou.module.supplychain.transfer.dal.dataobject.TransferOrderDO;

/**
 * 调拨单 Service 接口。
 *
 * <p>Source: TASK-G2-02S, PRD-组2-02 §4.2。
 */
public interface TransferOrderService {

    /**
     * 创建调拨单。
     * 校验: from_location_id ≠ to_location_id; 库位存在且 active; items 非空; quantity > 0。
     * 初始状态: status = PENDING。
     */
    TransferOrderDO createTransferOrder(TransferOrderCreateReqVO reqVO);

    /**
     * 发货（PENDING → SENT）。
     * 对每个 item 调用 StockEventService.recordEvent 写 TRANSFER_OUT 事件（源库位扣减）。
     * 记录 out_event_id。
     */
    TransferOrderDO shipTransferOrder(TransferOrderShipReqVO reqVO);

    /**
     * 收货（SENT → RECEIVED）。
     * 对每个 item 调用 StockEventService.recordEvent 写 TRANSFER_IN 事件（目标库位增加）。
     * 记录 in_event_id。
     */
    TransferOrderDO receiveTransferOrder(TransferOrderReceiveReqVO reqVO);

    /**
     * 取消（PENDING → CANCELLED）。
     * 不写任何库存事件（PENDING 状态下未发货，库存未变动）。
     * SENT 状态不可取消。
     */
    TransferOrderDO cancelTransferOrder(TransferOrderCancelReqVO reqVO);

    /**
     * 查询调拨单详情（含 items，租户隔离）。
     */
    TransferOrderDO getTransferOrder(Long id, Long tenantId);

    /**
     * 分页查询调拨单（租户隔离，可选状态筛选）。
     */
    PageResult<TransferOrderDO> listTransferOrders(Long tenantId, String status,
                                                    Integer pageNo, Integer pageSize);
}
