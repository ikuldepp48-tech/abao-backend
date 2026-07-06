package com.geihou.module.supplychain.transfer.service;

import com.geihou.common.pojo.PageResult;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLocationDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockItemMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockLocationMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import com.geihou.module.supplychain.stock.service.StockEventService;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderCancelReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderCreateReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderItemReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderReceiveReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderShipReqVO;
import com.geihou.module.supplychain.transfer.dal.dataobject.TransferOrderDO;
import com.geihou.module.supplychain.transfer.dal.dataobject.TransferOrderItemDO;
import com.geihou.module.supplychain.transfer.dal.mapper.TransferOrderItemMapper;
import com.geihou.module.supplychain.transfer.dal.mapper.TransferOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Implementation of {@link TransferOrderService}.
 *
 * <p>调拨单服务实现 — 创建/发货/收货/取消/查询。
 * 库存变更通过 StockEventService.recordEvent 写 TRANSFER_OUT/TRANSFER_IN 事件，不直接写 stock_balance。
 *
 * <p>状态机: PENDING → SENT → RECEIVED; PENDING → CANCELLED; SENT 不可取消。
 *
 * <p>Source: TASK-G2-02S, PRD-组2-02 §4.2, §4.3。
 */
@Service
public class TransferOrderServiceImpl implements TransferOrderService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_RECEIVED = "RECEIVED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private static final String SOURCE_MODULE = "transfer_order";

    @Autowired
    private TransferOrderMapper transferOrderMapper;
    @Autowired
    private TransferOrderItemMapper transferOrderItemMapper;
    @Autowired
    private StockEventService stockEventService;
    @Autowired
    private StockLocationMapper stockLocationMapper;
    @Autowired
    private StockItemMapper stockItemMapper;
    @Autowired
    private StockBalanceMapper stockBalanceMapper;

    // ==================== createTransferOrder ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrderDO createTransferOrder(TransferOrderCreateReqVO req) {
        Objects.requireNonNull(req, "request must not be null");
        if (req.getTenantId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "tenantId");
        }
        if (req.getCreatedBy() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "createdBy");
        }

        // 校验 from ≠ to
        if (req.getFromLocationId() == null || req.getToLocationId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "fromLocationId/toLocationId");
        }
        if (req.getFromLocationId().equals(req.getToLocationId())) {
            throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_FROM_TO_SAME);
        }

        // 校验源库位存在且 active
        StockLocationDO fromLocation = stockLocationMapper.selectByIdAndTenant(req.getFromLocationId(), req.getTenantId());
        if (fromLocation == null || !Boolean.TRUE.equals(fromLocation.getIsActive())) {
            throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_FROM_LOCATION_NOT_ACTIVE);
        }

        // 校验目标库位存在且 active
        StockLocationDO toLocation = stockLocationMapper.selectByIdAndTenant(req.getToLocationId(), req.getTenantId());
        if (toLocation == null || !Boolean.TRUE.equals(toLocation.getIsActive())) {
            throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_TO_LOCATION_NOT_ACTIVE);
        }

        // 校验 items 非空
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_ITEMS_EMPTY);
        }

        // 校验每个 item
        for (TransferOrderItemReqVO item : req.getItems()) {
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_QTY_MUST_BE_POSITIVE);
            }
            if (item.getStockItemId() == null) {
                throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "stockItemId");
            }
            StockItemDO stockItem = stockItemMapper.selectByIdAndTenant(item.getStockItemId(), req.getTenantId());
            if (stockItem == null) {
                throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_STOCK_ITEM_NOT_FOUND);
            }
        }

        // 生成 transferNo
        String transferNo = generateTransferNo(req.getTenantId());

        // 构建 DO
        LocalDateTime now = LocalDateTime.now();
        TransferOrderDO orderDO = new TransferOrderDO();
        orderDO.setTenantId(req.getTenantId());
        orderDO.setTransferNo(transferNo);
        orderDO.setFromLocationId(req.getFromLocationId());
        orderDO.setToLocationId(req.getToLocationId());
        orderDO.setStatus(STATUS_PENDING);
        orderDO.setCreatedBy(req.getCreatedBy());
        orderDO.setRemark(req.getRemark());
        orderDO.setCreator(String.valueOf(req.getCreatedBy()));
        orderDO.setCreateTime(now);
        orderDO.setUpdater(String.valueOf(req.getCreatedBy()));
        orderDO.setUpdateTime(now);
        orderDO.setDeleted(false);

        transferOrderMapper.insert(orderDO);

        // 插入 items
        List<TransferOrderItemDO> itemDOs = new ArrayList<>();
        for (TransferOrderItemReqVO item : req.getItems()) {
            TransferOrderItemDO itemDO = new TransferOrderItemDO();
            itemDO.setTenantId(req.getTenantId());
            itemDO.setTransferOrderId(orderDO.getId());
            itemDO.setProductId(item.getProductId());
            itemDO.setStockItemId(item.getStockItemId());
            itemDO.setSkuCode(item.getSkuCode());
            itemDO.setQuantity(item.getQuantity());
            itemDO.setUnit(item.getUnit());
            itemDO.setCreator(String.valueOf(req.getCreatedBy()));
            itemDO.setCreateTime(now);
            itemDO.setUpdater(String.valueOf(req.getCreatedBy()));
            itemDO.setUpdateTime(now);
            itemDO.setDeleted(false);
            transferOrderItemMapper.insert(itemDO);
            itemDOs.add(itemDO);
        }

        return orderDO;
    }

    // ==================== shipTransferOrder ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrderDO shipTransferOrder(TransferOrderShipReqVO req) {
        Objects.requireNonNull(req, "request must not be null");
        TransferOrderDO order = transferOrderMapper.selectByIdAndTenant(req.getId(), req.getTenantId());
        if (order == null) {
            throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_ORDER_NOT_FOUND);
        }
        if (!STATUS_PENDING.equals(order.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_INVALID_STATUS,
                    "current status: " + order.getStatus() + ", expected: PENDING");
        }

        List<TransferOrderItemDO> items = transferOrderItemMapper.listByOrderAndTenant(order.getId(), order.getTenantId());

        // 发货前库存校验 + 写 TRANSFER_OUT 事件
        for (TransferOrderItemDO item : items) {
            StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(
                    order.getTenantId(), item.getStockItemId(), order.getFromLocationId());
            if (balance == null || balance.getAvailableQty().compareTo(item.getQuantity()) < 0) {
                throw new StockBusinessException(StockErrorCodeConstants.INSUFFICIENT_FOR_TRANSFER,
                        "stockItemId=" + item.getStockItemId() + ", locationId=" + order.getFromLocationId());
            }

            // 写 TRANSFER_OUT 事件
            Long eventId = recordTransferEvent(order, item, StockEventTypeEnum.TRANSFER_OUT,
                    StockDirectionEnum.OUT, order.getFromLocationId(),
                    "transfer-ship-" + order.getId() + "-" + item.getId(),
                    req.getShippedBy());

            // 记录 out_event_id
            transferOrderItemMapper.updateOutEventId(item.getId(), order.getTenantId(), eventId,
                    String.valueOf(req.getShippedBy()), LocalDateTime.now());
        }

        // 状态更新 PENDING → SENT
        LocalDateTime now = LocalDateTime.now();
        int rows = transferOrderMapper.updateStatusByTenant(
                order.getId(), order.getTenantId(),
                STATUS_SENT, STATUS_PENDING,
                req.getShippedBy(), now,
                null, null,
                null, null,
                null,
                String.valueOf(req.getShippedBy()), now);
        if (rows == 0) {
            throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_INVALID_STATUS,
                    "concurrent modification detected");
        }

        return transferOrderMapper.selectByIdAndTenant(order.getId(), order.getTenantId());
    }

    // ==================== receiveTransferOrder ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrderDO receiveTransferOrder(TransferOrderReceiveReqVO req) {
        Objects.requireNonNull(req, "request must not be null");
        TransferOrderDO order = transferOrderMapper.selectByIdAndTenant(req.getId(), req.getTenantId());
        if (order == null) {
            throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_ORDER_NOT_FOUND);
        }
        if (!STATUS_SENT.equals(order.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_INVALID_STATUS,
                    "current status: " + order.getStatus() + ", expected: SENT");
        }

        List<TransferOrderItemDO> items = transferOrderItemMapper.listByOrderAndTenant(order.getId(), order.getTenantId());

        // 写 TRANSFER_IN 事件
        for (TransferOrderItemDO item : items) {
            Long eventId = recordTransferEvent(order, item, StockEventTypeEnum.TRANSFER_IN,
                    StockDirectionEnum.IN, order.getToLocationId(),
                    "transfer-receive-" + order.getId() + "-" + item.getId(),
                    req.getReceivedBy());

            // 记录 in_event_id
            transferOrderItemMapper.updateInEventId(item.getId(), order.getTenantId(), eventId,
                    String.valueOf(req.getReceivedBy()), LocalDateTime.now());
        }

        // 状态更新 SENT → RECEIVED
        LocalDateTime now = LocalDateTime.now();
        int rows = transferOrderMapper.updateStatusByTenant(
                order.getId(), order.getTenantId(),
                STATUS_RECEIVED, STATUS_SENT,
                null, null,
                req.getReceivedBy(), now,
                null, null,
                null,
                String.valueOf(req.getReceivedBy()), now);
        if (rows == 0) {
            throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_INVALID_STATUS,
                    "concurrent modification detected");
        }

        return transferOrderMapper.selectByIdAndTenant(order.getId(), order.getTenantId());
    }

    // ==================== cancelTransferOrder ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrderDO cancelTransferOrder(TransferOrderCancelReqVO req) {
        Objects.requireNonNull(req, "request must not be null");
        TransferOrderDO order = transferOrderMapper.selectByIdAndTenant(req.getId(), req.getTenantId());
        if (order == null) {
            throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_ORDER_NOT_FOUND);
        }
        if (!STATUS_PENDING.equals(order.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_CANCEL_NOT_ALLOWED,
                    "current status: " + order.getStatus() + ", only PENDING can be cancelled");
        }
        // 校验 cancelReason 非空（明确校验：null 或空白均不合法）
        if (req.getCancelReason() == null || req.getCancelReason().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_CANCEL_NOT_ALLOWED,
                    "cancelReason must not be null or blank");
        }

        LocalDateTime now = LocalDateTime.now();
        int rows = transferOrderMapper.updateStatusByTenant(
                order.getId(), order.getTenantId(),
                STATUS_CANCELLED, STATUS_PENDING,
                null, null,
                null, null,
                req.getCancelledBy(), now,
                req.getCancelReason(),
                String.valueOf(req.getCancelledBy()), now);
        if (rows == 0) {
            throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_CANCEL_NOT_ALLOWED,
                    "concurrent modification detected");
        }

        // 不写任何库存事件（PENDING 状态下未发货，库存未变动）
        return transferOrderMapper.selectByIdAndTenant(order.getId(), order.getTenantId());
    }

    // ==================== getTransferOrder ====================

    @Override
    public TransferOrderDO getTransferOrder(Long id, Long tenantId) {
        return transferOrderMapper.selectByIdAndTenant(id, tenantId);
    }

    // ==================== listTransferOrders ====================

    @Override
    public PageResult<TransferOrderDO> listTransferOrders(Long tenantId, String status,
                                                            Integer pageNo, Integer pageSize) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(pageNo, "pageNo must not be null");
        Objects.requireNonNull(pageSize, "pageSize must not be null");

        if (status != null && !status.isBlank()) {
            return transferOrderMapper.selectPage(
                    TransferOrderDO::getTenantId, tenantId,
                    TransferOrderDO::getStatus, status,
                    pageNo, pageSize);
        } else {
            return transferOrderMapper.selectPage(
                    TransferOrderDO::getTenantId, tenantId,
                    pageNo, pageSize);
        }
    }

    // ==================== Private helpers ====================

    /**
     * Record a TRANSFER_OUT or TRANSFER_IN event via StockEventService.
     */
    private Long recordTransferEvent(TransferOrderDO order, TransferOrderItemDO item,
                                      StockEventTypeEnum eventType, StockDirectionEnum direction,
                                      Long locationId, String clientRequestId, Long operatorUserId) {
        StockEventReqDTO eventReq = new StockEventReqDTO();
        eventReq.setTenantId(order.getTenantId());
        eventReq.setEventTime(LocalDateTime.now());
        eventReq.setBusinessDate(LocalDateTime.now().toLocalDate());
        eventReq.setEventType(eventType.getCode());
        eventReq.setDirection(direction.getCode());
        eventReq.setStockItemId(item.getStockItemId());
        eventReq.setSkuCode(item.getSkuCode());
        eventReq.setLocationId(locationId);
        eventReq.setQuantity(item.getQuantity());
        eventReq.setUnit(item.getUnit());
        eventReq.setSourceModule(SOURCE_MODULE);
        eventReq.setSourceRecordId(order.getId());
        eventReq.setReferenceNo(order.getTransferNo());
        eventReq.setClientRequestId(clientRequestId);
        eventReq.setOperatorUserId(operatorUserId);
        return stockEventService.recordEvent(eventReq);
    }

    /**
     * Generate a unique transfer number: TO{yyyyMMddHHmmss}{4位随机}
     * Checks for duplicates via mapper and retries up to MAX_RETRIES times.
     * Falls back to DB unique constraint as final safety net.
     */
    private String generateTransferNo(Long tenantId) {
        final int MAX_RETRIES = 5;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            int seq = ThreadLocalRandom.current().nextInt(1000, 9999);
            String transferNo = "TO" + timestamp + seq;
            // 查重：用 mapper 确认租户内不存在相同 transferNo
            TransferOrderDO existing = transferOrderMapper.selectByTenantAndTransferNo(tenantId, transferNo);
            if (existing == null) {
                return transferNo;
            }
        }
        // 重试耗尽后仍冲突，抛异常让 DB unique 约束作为最终兜底
        throw new StockBusinessException(StockErrorCodeConstants.TRANSFER_INVALID_STATUS,
                "Failed to generate unique transfer number after " + MAX_RETRIES + " attempts");
    }
}
