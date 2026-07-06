package com.geihou.module.supplychain.transfer.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderCancelReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderCreateReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderItemRespVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderReceiveReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderRespVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderShipReqVO;
import com.geihou.module.supplychain.transfer.dal.dataobject.TransferOrderDO;
import com.geihou.module.supplychain.transfer.dal.dataobject.TransferOrderItemDO;
import com.geihou.module.supplychain.transfer.dal.mapper.TransferOrderItemMapper;
import com.geihou.module.supplychain.transfer.service.TransferOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin controller for transfer orders (G2-02S).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /admin-api/supplychain/transfer/orders} — create transfer order</li>
 *   <li>{@code POST /admin-api/supplychain/transfer/orders/{id}/ship} — ship</li>
 *   <li>{@code POST /admin-api/supplychain/transfer/orders/{id}/receive} — receive</li>
 *   <li>{@code POST /admin-api/supplychain/transfer/orders/{id}/cancel} — cancel</li>
 *   <li>{@code GET  /admin-api/supplychain/transfer/orders/{id}} — get by ID</li>
 *   <li>{@code GET  /admin-api/supplychain/transfer/orders/page} — paged query</li>
 * </ul>
 *
 * <p>Source: TASK-G2-02S.
 */
@RestController
@RequestMapping("/admin-api/supplychain/transfer/orders")
public class TransferOrderController {

    @Autowired
    private TransferOrderService transferOrderService;

    @Autowired
    private TransferOrderItemMapper transferOrderItemMapper;

    /** 创建调拨单 */
    @PostMapping
    public CommonResult<TransferOrderRespVO> create(@RequestBody TransferOrderCreateReqVO req) {
        TransferOrderDO order = transferOrderService.createTransferOrder(req);
        return CommonResult.success(toRespVO(order));
    }

    /** 发货 */
    @PostMapping("/{id}/ship")
    public CommonResult<TransferOrderRespVO> ship(@PathVariable Long id,
                                                   @RequestBody TransferOrderShipReqVO req) {
        req.setId(id);
        TransferOrderDO order = transferOrderService.shipTransferOrder(req);
        return CommonResult.success(toRespVO(order));
    }

    /** 收货 */
    @PostMapping("/{id}/receive")
    public CommonResult<TransferOrderRespVO> receive(@PathVariable Long id,
                                                      @RequestBody TransferOrderReceiveReqVO req) {
        req.setId(id);
        TransferOrderDO order = transferOrderService.receiveTransferOrder(req);
        return CommonResult.success(toRespVO(order));
    }

    /** 取消 */
    @PostMapping("/{id}/cancel")
    public CommonResult<TransferOrderRespVO> cancel(@PathVariable Long id,
                                                     @RequestBody TransferOrderCancelReqVO req) {
        req.setId(id);
        TransferOrderDO order = transferOrderService.cancelTransferOrder(req);
        return CommonResult.success(toRespVO(order));
    }

    /** 查询详情 */
    @GetMapping("/{id}")
    public CommonResult<TransferOrderRespVO> get(@PathVariable Long id,
                                                  @RequestParam Long tenantId) {
        TransferOrderDO order = transferOrderService.getTransferOrder(id, tenantId);
        return CommonResult.success(toRespVO(order));
    }

    /** 分页查询 */
    @GetMapping("/page")
    public CommonResult<PageResult<TransferOrderRespVO>> page(
            @RequestParam Long tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<TransferOrderDO> page = transferOrderService.listTransferOrders(tenantId, status, pageNo, pageSize);
        List<TransferOrderRespVO> voList = page.getList().stream()
                .map(this::toRespVO)
                .collect(Collectors.toList());
        PageResult<TransferOrderRespVO> voPage = PageResult.of(voList, page.getTotal(), page.getPageNo(), page.getPageSize());
        return CommonResult.success(voPage);
    }

    // --- Helper ---

    private TransferOrderRespVO toRespVO(TransferOrderDO order) {
        if (order == null) {
            return null;
        }
        TransferOrderRespVO vo = new TransferOrderRespVO();
        vo.setId(order.getId());
        vo.setTenantId(order.getTenantId());
        vo.setTransferNo(order.getTransferNo());
        vo.setFromLocationId(order.getFromLocationId());
        vo.setToLocationId(order.getToLocationId());
        vo.setStatus(order.getStatus());
        vo.setCreatedBy(order.getCreatedBy());
        vo.setShippedBy(order.getShippedBy());
        vo.setReceivedBy(order.getReceivedBy());
        vo.setCancelledBy(order.getCancelledBy());
        vo.setShippedAt(order.getShippedAt());
        vo.setReceivedAt(order.getReceivedAt());
        vo.setCancelledAt(order.getCancelledAt());
        vo.setRemark(order.getRemark());
        vo.setCancelReason(order.getCancelReason());
        vo.setCreator(order.getCreator());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdater(order.getUpdater());
        vo.setUpdateTime(order.getUpdateTime());

        // Load items
        List<TransferOrderItemDO> items = transferOrderItemMapper.listByOrderAndTenant(order.getId(), order.getTenantId());
        List<TransferOrderItemRespVO> itemVOs = items.stream().map(this::toItemRespVO).collect(Collectors.toList());
        vo.setItems(itemVOs);

        return vo;
    }

    private TransferOrderItemRespVO toItemRespVO(TransferOrderItemDO item) {
        TransferOrderItemRespVO vo = new TransferOrderItemRespVO();
        vo.setId(item.getId());
        vo.setTransferOrderId(item.getTransferOrderId());
        vo.setProductId(item.getProductId());
        vo.setStockItemId(item.getStockItemId());
        vo.setSkuCode(item.getSkuCode());
        vo.setQuantity(item.getQuantity());
        vo.setUnit(item.getUnit());
        vo.setOutEventId(item.getOutEventId());
        vo.setInEventId(item.getInEventId());
        return vo;
    }
}
