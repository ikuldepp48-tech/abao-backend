package com.geihou.module.supplychain.transfer.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLocationDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockItemMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockLocationMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderCancelReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderCreateReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderItemReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderReceiveReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderShipReqVO;
import com.geihou.module.supplychain.transfer.dal.dataobject.TransferOrderDO;
import com.geihou.module.supplychain.transfer.dal.dataobject.TransferOrderItemDO;
import com.geihou.module.supplychain.transfer.dal.mapper.TransferOrderItemMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TransferOrderService}.
 *
 * <p>Covers: create, ship, receive, cancel, query, state machine validation,
 * tenant isolation, stock event verification, idempotency.
 *
 * <p>Source: TASK-G2-02S Section 11.1.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:transfer_order_service_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class TransferOrderServiceTest {

    @Autowired
    private TransferOrderService transferOrderService;
    @Autowired
    private TransferOrderItemMapper transferOrderItemMapper;
    @Autowired
    private StockBalanceMapper stockBalanceMapper;
    @Autowired
    private StockEventMapper stockEventMapper;
    @Autowired
    private StockLocationMapper stockLocationMapper;
    @Autowired
    private StockItemMapper stockItemMapper;
    @Autowired
    private DataSource dataSource;

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long STOCK_ITEM_ID = 2001L;
    private static final Long FROM_LOCATION_ID = 10L;
    private static final Long TO_LOCATION_ID = 20L;
    private static final String SKU_CODE = "SKU_TRANSFER";
    private static final Long OPERATOR_ID = 100L;
    private static final Long SHIPPED_BY = 200L;
    private static final Long RECEIVED_BY = 300L;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(TENANT_A);
        seedLocation(TENANT_A, FROM_LOCATION_ID, "LOC_FROM", "MAIN");
        seedLocation(TENANT_A, TO_LOCATION_ID, "LOC_TO", "MAIN");
        seedStockItem(TENANT_A, STOCK_ITEM_ID, SKU_CODE);
        seedBalance(TENANT_A, STOCK_ITEM_ID, FROM_LOCATION_ID, new BigDecimal("100"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ==================== Create ====================

    @Test
    void testCreateSuccess() {
        TransferOrderCreateReqVO req = buildCreateReq();
        TransferOrderDO order = transferOrderService.createTransferOrder(req);

        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo("PENDING");
        assertThat(order.getTransferNo()).startsWith("TO");
        assertThat(order.getFromLocationId()).isEqualTo(FROM_LOCATION_ID);
        assertThat(order.getToLocationId()).isEqualTo(TO_LOCATION_ID);

        List<TransferOrderItemDO> items = transferOrderItemMapper.listByOrderAndTenant(order.getId(), order.getTenantId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSkuCode()).isEqualTo(SKU_CODE);
        assertThat(items.get(0).getQuantity()).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void testCreateFromToSame() {
        TransferOrderCreateReqVO req = buildCreateReq();
        req.setToLocationId(FROM_LOCATION_ID);
        assertThatThrownBy(() -> transferOrderService.createTransferOrder(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("same");
    }

    @Test
    void testCreateFromLocationNotActive() {
        // Make from location inactive
        stockLocationMapper.updateActiveByIdAndTenant(FROM_LOCATION_ID, TENANT_A, false, "test", LocalDateTime.now());
        TransferOrderCreateReqVO req = buildCreateReq();
        assertThatThrownBy(() -> transferOrderService.createTransferOrder(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("From stock location");
    }

    @Test
    void testCreateToLocationNotFound() {
        TransferOrderCreateReqVO req = buildCreateReq();
        req.setToLocationId(99999L);
        assertThatThrownBy(() -> transferOrderService.createTransferOrder(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("To stock location");
    }

    @Test
    void testCreateItemsEmpty() {
        TransferOrderCreateReqVO req = buildCreateReq();
        req.setItems(List.of());
        assertThatThrownBy(() -> transferOrderService.createTransferOrder(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("items must not be empty");
    }

    @Test
    void testCreateQtyZero() {
        TransferOrderCreateReqVO req = buildCreateReq();
        req.getItems().get(0).setQuantity(BigDecimal.ZERO);
        assertThatThrownBy(() -> transferOrderService.createTransferOrder(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void testCreateStockItemNotFound() {
        TransferOrderCreateReqVO req = buildCreateReq();
        req.getItems().get(0).setStockItemId(99999L);
        assertThatThrownBy(() -> transferOrderService.createTransferOrder(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Stock item not found");
    }

    // ==================== Ship ====================

    @Test
    void testShipSuccess() {
        TransferOrderDO order = createOrder();
        TransferOrderShipReqVO shipReq = buildShipReq(order.getId());
        TransferOrderDO shipped = transferOrderService.shipTransferOrder(shipReq);

        assertThat(shipped.getStatus()).isEqualTo("SENT");
        assertThat(shipped.getShippedBy()).isEqualTo(SHIPPED_BY);
        assertThat(shipped.getShippedAt()).isNotNull();

        // Verify out_event_id recorded
        List<TransferOrderItemDO> items = transferOrderItemMapper.listByOrderAndTenant(order.getId(), order.getTenantId());
        assertThat(items.get(0).getOutEventId()).isNotNull();
        assertThat(items.get(0).getOutEventId()).isGreaterThan(0L);

        // Verify TRANSFER_OUT event
        StockEventDO event = stockEventMapper.selectById(items.get(0).getOutEventId());
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo("TRANSFER_OUT");
        assertThat(event.getDirection()).isEqualTo("OUT");
        assertThat(event.getSourceModule()).isEqualTo("transfer_order");
        assertThat(event.getSourceRecordId()).isEqualTo(order.getId());
        assertThat(event.getReferenceNo()).isEqualTo(order.getTransferNo());
        assertThat(event.getClientRequestId()).isEqualTo("transfer-ship-" + order.getId() + "-" + items.get(0).getId());
        assertThat(event.getLocationId()).isEqualTo(FROM_LOCATION_ID);

        // Verify stock deducted: 100 - 5 = 95
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(TENANT_A, STOCK_ITEM_ID, FROM_LOCATION_ID);
        assertThat(balance.getAvailableQty()).isEqualByComparingTo(new BigDecimal("95"));
    }

    @Test
    void testShipInsufficientStock() {
        seedBalance(TENANT_A, STOCK_ITEM_ID, FROM_LOCATION_ID, new BigDecimal("3"));
        TransferOrderDO order = createOrder();
        TransferOrderShipReqVO shipReq = buildShipReq(order.getId());
        assertThatThrownBy(() -> transferOrderService.shipTransferOrder(shipReq))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void testShipIdempotent() {
        TransferOrderDO order = createOrder();
        TransferOrderShipReqVO shipReq = buildShipReq(order.getId());

        // First ship succeeds
        transferOrderService.shipTransferOrder(shipReq);

        // Second ship → TRANSFER_INVALID_STATUS (already SENT, not PENDING)
        assertThatThrownBy(() -> transferOrderService.shipTransferOrder(shipReq))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Invalid transfer order status");

        // Verify no duplicate events
        List<TransferOrderItemDO> items = transferOrderItemMapper.listByOrderAndTenant(order.getId(), order.getTenantId());
        assertThat(items.get(0).getOutEventId()).isNotNull();
        StockEventDO event = stockEventMapper.selectById(items.get(0).getOutEventId());
        assertThat(event).isNotNull();
    }

    @Test
    void testShipNotFound() {
        TransferOrderShipReqVO shipReq = buildShipReq(99999L);
        assertThatThrownBy(() -> transferOrderService.shipTransferOrder(shipReq))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void testShipCrossTenant() {
        TransferOrderDO order = createOrder();
        TransferOrderShipReqVO shipReq = buildShipReq(order.getId());
        shipReq.setTenantId(TENANT_B);
        assertThatThrownBy(() -> transferOrderService.shipTransferOrder(shipReq))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void testShipInvalidStatus() {
        TransferOrderDO order = createOrder();
        TransferOrderShipReqVO shipReq = buildShipReq(order.getId());
        // Ship once → SENT
        transferOrderService.shipTransferOrder(shipReq);
        // Ship again → already SENT, not PENDING
        assertThatThrownBy(() -> transferOrderService.shipTransferOrder(shipReq))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Invalid transfer order status");
    }

    // ==================== Receive ====================

    @Test
    void testReceiveSuccess() {
        TransferOrderDO order = createOrder();
        // Ship first
        transferOrderService.shipTransferOrder(buildShipReq(order.getId()));

        // Receive
        TransferOrderReceiveReqVO receiveReq = buildReceiveReq(order.getId());
        TransferOrderDO received = transferOrderService.receiveTransferOrder(receiveReq);

        assertThat(received.getStatus()).isEqualTo("RECEIVED");
        assertThat(received.getReceivedBy()).isEqualTo(RECEIVED_BY);
        assertThat(received.getReceivedAt()).isNotNull();

        // Verify in_event_id recorded
        List<TransferOrderItemDO> items = transferOrderItemMapper.listByOrderAndTenant(order.getId(), order.getTenantId());
        assertThat(items.get(0).getInEventId()).isNotNull();
        assertThat(items.get(0).getInEventId()).isGreaterThan(0L);

        // Verify TRANSFER_IN event
        StockEventDO event = stockEventMapper.selectById(items.get(0).getInEventId());
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo("TRANSFER_IN");
        assertThat(event.getDirection()).isEqualTo("IN");
        assertThat(event.getSourceModule()).isEqualTo("transfer_order");
        assertThat(event.getSourceRecordId()).isEqualTo(order.getId());
        assertThat(event.getReferenceNo()).isEqualTo(order.getTransferNo());
        assertThat(event.getClientRequestId()).isEqualTo("transfer-receive-" + order.getId() + "-" + items.get(0).getId());
        assertThat(event.getLocationId()).isEqualTo(TO_LOCATION_ID);

        // Verify target stock increased: 0 + 5 = 5
        StockBalanceDO toBalance = stockBalanceMapper.selectByTenantItemLocation(TENANT_A, STOCK_ITEM_ID, TO_LOCATION_ID);
        assertThat(toBalance.getAvailableQty()).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void testReceiveIdempotent() {
        TransferOrderDO order = createOrder();
        transferOrderService.shipTransferOrder(buildShipReq(order.getId()));

        TransferOrderReceiveReqVO receiveReq = buildReceiveReq(order.getId());
        // First receive succeeds
        transferOrderService.receiveTransferOrder(receiveReq);

        // Second receive → TRANSFER_INVALID_STATUS (already RECEIVED, not SENT)
        assertThatThrownBy(() -> transferOrderService.receiveTransferOrder(receiveReq))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Invalid transfer order status");

        // Verify no duplicate events
        List<TransferOrderItemDO> items = transferOrderItemMapper.listByOrderAndTenant(order.getId(), order.getTenantId());
        assertThat(items.get(0).getInEventId()).isNotNull();
    }

    @Test
    void testReceiveInvalidStatus() {
        TransferOrderDO order = createOrder();
        // PENDING → directly receive without shipping
        TransferOrderReceiveReqVO receiveReq = buildReceiveReq(order.getId());
        assertThatThrownBy(() -> transferOrderService.receiveTransferOrder(receiveReq))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Invalid transfer order status");
    }

    // ==================== Cancel ====================

    @Test
    void testCancelFromPending() {
        TransferOrderDO order = createOrder();
        TransferOrderCancelReqVO cancelReq = buildCancelReq(order.getId());
        TransferOrderDO cancelled = transferOrderService.cancelTransferOrder(cancelReq);

        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getCancelledBy()).isEqualTo(OPERATOR_ID);
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(cancelled.getCancelReason()).isEqualTo("test cancel reason");

        // Verify no stock events written
        List<TransferOrderItemDO> items = transferOrderItemMapper.listByOrderAndTenant(order.getId(), order.getTenantId());
        assertThat(items.get(0).getOutEventId()).isNull();
        assertThat(items.get(0).getInEventId()).isNull();

        // Verify stock unchanged
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(TENANT_A, STOCK_ITEM_ID, FROM_LOCATION_ID);
        assertThat(balance.getAvailableQty()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void testCancelFromSentNotAllowed() {
        TransferOrderDO order = createOrder();
        transferOrderService.shipTransferOrder(buildShipReq(order.getId()));

        TransferOrderCancelReqVO cancelReq = buildCancelReq(order.getId());
        assertThatThrownBy(() -> transferOrderService.cancelTransferOrder(cancelReq))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("cannot be cancelled");
    }

    @Test
    void testCancelFromReceivedNotAllowed() {
        TransferOrderDO order = createOrder();
        transferOrderService.shipTransferOrder(buildShipReq(order.getId()));
        transferOrderService.receiveTransferOrder(buildReceiveReq(order.getId()));

        TransferOrderCancelReqVO cancelReq = buildCancelReq(order.getId());
        assertThatThrownBy(() -> transferOrderService.cancelTransferOrder(cancelReq))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("cannot be cancelled");
    }

    @Test
    void testCancelReasonRequired() {
        TransferOrderDO order = createOrder();
        TransferOrderCancelReqVO cancelReq = buildCancelReq(order.getId());
        cancelReq.setCancelReason(null);
        assertThatThrownBy(() -> transferOrderService.cancelTransferOrder(cancelReq))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("cancelReason");

        // Test blank reason too
        cancelReq.setCancelReason("  ");
        assertThatThrownBy(() -> transferOrderService.cancelTransferOrder(cancelReq))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("cancelReason");
    }

    // ==================== List / Query ====================

    @Test
    void testListByTenantAll() {
        createOrder();
        createOrder();
        var page = transferOrderService.listTransferOrders(TENANT_A, null, 1, 10);
        assertThat(page.getList()).hasSize(2);
        assertThat(page.getTotal()).isEqualTo(2L);
    }

    @Test
    void testListByTenantAndStatus() {
        TransferOrderDO order1 = createOrder();
        TransferOrderDO order2 = createOrder();
        // Ship order1
        transferOrderService.shipTransferOrder(buildShipReq(order1.getId()));

        var pendingPage = transferOrderService.listTransferOrders(TENANT_A, "PENDING", 1, 10);
        assertThat(pendingPage.getList()).hasSize(1);
        assertThat(pendingPage.getList().get(0).getId()).isEqualTo(order2.getId());

        var sentPage = transferOrderService.listTransferOrders(TENANT_A, "SENT", 1, 10);
        assertThat(sentPage.getList()).hasSize(1);
        assertThat(sentPage.getList().get(0).getId()).isEqualTo(order1.getId());
    }

    @Test
    void testListCrossTenant() {
        createOrder();
        var pageB = transferOrderService.listTransferOrders(TENANT_B, null, 1, 10);
        assertThat(pageB.getList()).isEmpty();
        assertThat(pageB.getTotal()).isEqualTo(0L);
    }

    // ==================== No Direct Balance Write ====================

    @Test
    void testNoDirectBalanceWrite() {
        TransferOrderDO order = createOrder();
        transferOrderService.shipTransferOrder(buildShipReq(order.getId()));
        transferOrderService.receiveTransferOrder(buildReceiveReq(order.getId()));

        // Verify events were written through StockEventService (not direct balance writes)
        List<TransferOrderItemDO> items = transferOrderItemMapper.listByOrderAndTenant(order.getId(), order.getTenantId());
        Long outEventId = items.get(0).getOutEventId();
        Long inEventId = items.get(0).getInEventId();
        assertThat(outEventId).isNotNull();
        assertThat(inEventId).isNotNull();

        StockEventDO outEvent = stockEventMapper.selectById(outEventId);
        StockEventDO inEvent = stockEventMapper.selectById(inEventId);
        assertThat(outEvent.getEventType()).isEqualTo("TRANSFER_OUT");
        assertThat(inEvent.getEventType()).isEqualTo("TRANSFER_IN");

        // Both events should have source_module = transfer_order
        assertThat(outEvent.getSourceModule()).isEqualTo("transfer_order");
        assertThat(inEvent.getSourceModule()).isEqualTo("transfer_order");
    }

    // ==================== Helpers ====================

    private TransferOrderCreateReqVO buildCreateReq() {
        TransferOrderCreateReqVO req = new TransferOrderCreateReqVO();
        req.setTenantId(TENANT_A);
        req.setFromLocationId(FROM_LOCATION_ID);
        req.setToLocationId(TO_LOCATION_ID);
        req.setCreatedBy(OPERATOR_ID);
        req.setRemark("test transfer");

        TransferOrderItemReqVO item = new TransferOrderItemReqVO();
        item.setProductId(1L);
        item.setStockItemId(STOCK_ITEM_ID);
        item.setSkuCode(SKU_CODE);
        item.setQuantity(new BigDecimal("5"));
        item.setUnit("KG");
        req.setItems(List.of(item));
        return req;
    }

    private TransferOrderShipReqVO buildShipReq(Long id) {
        TransferOrderShipReqVO req = new TransferOrderShipReqVO();
        req.setId(id);
        req.setTenantId(TENANT_A);
        req.setShippedBy(SHIPPED_BY);
        return req;
    }

    private TransferOrderReceiveReqVO buildReceiveReq(Long id) {
        TransferOrderReceiveReqVO req = new TransferOrderReceiveReqVO();
        req.setId(id);
        req.setTenantId(TENANT_A);
        req.setReceivedBy(RECEIVED_BY);
        return req;
    }

    private TransferOrderCancelReqVO buildCancelReq(Long id) {
        TransferOrderCancelReqVO req = new TransferOrderCancelReqVO();
        req.setId(id);
        req.setTenantId(TENANT_A);
        req.setCancelledBy(OPERATOR_ID);
        req.setCancelReason("test cancel reason");
        return req;
    }

    private TransferOrderDO createOrder() {
        return transferOrderService.createTransferOrder(buildCreateReq());
    }

    private void seedLocation(Long tenantId, Long locationId, String code, String type) {
        StockLocationDO loc = new StockLocationDO();
        loc.setId(locationId);
        loc.setTenantId(tenantId);
        loc.setLocationCode(code);
        loc.setLocationName(code);
        loc.setLocationType(type);
        loc.setIsActive(true);
        loc.setCreator("system");
        loc.setCreateTime(LocalDateTime.now());
        loc.setUpdater("system");
        loc.setUpdateTime(LocalDateTime.now());
        loc.setDeleted(false);
        stockLocationMapper.insert(loc);
    }

    private void seedStockItem(Long tenantId, Long itemId, String skuCode) {
        StockItemDO item = new StockItemDO();
        item.setId(itemId);
        item.setTenantId(tenantId);
        item.setSkuCode(skuCode);
        item.setItemName(skuCode);
        item.setUnit("KG");
        item.setIsRawMaterial(true);
        item.setIsActive(true);
        item.setCreator("system");
        item.setCreateTime(LocalDateTime.now());
        item.setUpdater("system");
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleted(false);
        stockItemMapper.insert(item);
    }

    private void seedBalance(Long tenantId, Long stockItemId, Long locationId, BigDecimal availableQty) {
        StockBalanceDO existing = stockBalanceMapper.selectByTenantItemLocation(tenantId, stockItemId, locationId);
        if (existing != null) {
            stockBalanceMapper.deleteById(existing.getId());
        }
        StockBalanceDO balance = new StockBalanceDO();
        balance.setTenantId(tenantId);
        balance.setStockItemId(stockItemId);
        balance.setLocationId(locationId);
        balance.setAvailableQty(availableQty);
        balance.setTotalQty(availableQty);
        balance.setReservedQty(BigDecimal.ZERO);
        balance.setAvgUnitCost(BigDecimal.ZERO);
        balance.setVersion(0);
        balance.setCreator("system");
        balance.setCreateTime(LocalDateTime.now());
        balance.setUpdater("system");
        balance.setUpdateTime(LocalDateTime.now());
        balance.setDeleted(false);
        stockBalanceMapper.insert(balance);
    }
}
