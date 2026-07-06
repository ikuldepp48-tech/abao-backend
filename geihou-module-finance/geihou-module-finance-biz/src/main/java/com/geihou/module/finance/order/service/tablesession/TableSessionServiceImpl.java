package com.geihou.module.finance.order.service.tablesession;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderTableSessionDO;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.dal.mapper.OrderTableSessionMapper;
import com.geihou.module.finance.order.enums.TableSessionStatusEnum;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.framework.OrderErrorCodeConstants;
import com.geihou.module.finance.order.service.BusinessDateCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Table session service implementation.
 *
 * <p>Tenant isolation via TenantContextHolder + MyBatis-Plus TenantLineInnerInterceptor.
 * All money fields use BigDecimal (never double/float).
 *
 * <p>State transition whitelist (CG-TS2):
 * <ul>
 *   <li>OPEN → ORDERING</li>
 *   <li>ORDERING → SERVING, SETTLING</li>
 *   <li>SERVING → SETTLING</li>
 *   <li>SETTLING → CLOSED</li>
 *   <li>CLOSED → (terminal, no transitions)</li>
 * </ul>
 */
@Service
public class TableSessionServiceImpl implements TableSessionService {

    private final OrderTableSessionMapper sessionMapper;
    private final OrderMapper orderMapper;
    private final BusinessDateCalculator businessDateCalculator;

    public TableSessionServiceImpl(OrderTableSessionMapper sessionMapper,
                                   OrderMapper orderMapper,
                                   BusinessDateCalculator businessDateCalculator) {
        this.sessionMapper = sessionMapper;
        this.orderMapper = orderMapper;
        this.businessDateCalculator = businessDateCalculator;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderTableSessionDO openSession(Long shopId, Long tableId, String tableNo, Integer customerCount) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        LocalDateTime now = LocalDateTime.now();
        LocalDate businessDate = businessDateCalculator.computeBusinessDate(now);

        OrderTableSessionDO session = new OrderTableSessionDO();
        session.setTenantId(tenantId);
        session.setShopId(shopId);
        session.setTableId(tableId);
        session.setTableNo(tableNo);
        session.setSessionNo(generateSessionNo(tenantId));
        session.setCustomerCount(customerCount);
        session.setStatus(TableSessionStatusEnum.OPEN.getCode());
        session.setOpenTime(now);
        session.setBusinessDate(businessDate);
        session.setTotalAmount(BigDecimal.ZERO);
        session.setPaidAmount(BigDecimal.ZERO);
        session.setOrderCount(0);
        session.setCreator("");
        session.setCreateTime(now);
        session.setUpdater("");
        session.setUpdateTime(now);
        session.setDeleted(false);

        sessionMapper.insert(session);
        return session;
    }

    @Override
    public OrderTableSessionDO getBySessionNo(String sessionNo) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        return sessionMapper.selectOne(
                OrderTableSessionDO::getSessionNo, sessionNo,
                OrderTableSessionDO::getTenantId, tenantId
        );
    }

    @Override
    public List<OrderDO> getSessionOrders(Long sessionId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        return orderMapper.selectList(
                OrderDO::getTableSessionId, sessionId,
                OrderDO::getTenantId, tenantId
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderTableSessionDO settleSession(String sessionNo) {
        OrderTableSessionDO session = getBySessionNo(sessionNo);
        if (session == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.TABLE_SESSION_NOT_FOUND);
        }

        String currentStatus = session.getStatus();
        // ORDERING and SERVING can transition to SETTLING (CG-TS2)
        if (!TableSessionStatusEnum.ORDERING.getCode().equals(currentStatus) &&
            !TableSessionStatusEnum.SERVING.getCode().equals(currentStatus)) {
            if (TableSessionStatusEnum.CLOSED.getCode().equals(currentStatus)) {
                throw new OrderBusinessException(OrderErrorCodeConstants.TABLE_SESSION_ALREADY_CLOSED);
            }
            throw new OrderBusinessException(OrderErrorCodeConstants.TABLE_SESSION_INVALID,
                    "cannot settle from status=" + currentStatus);
        }

        // Aggregate from associated orders
        List<OrderDO> orders = getSessionOrders(session.getId());
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal paidAmount = BigDecimal.ZERO;
        for (OrderDO order : orders) {
            totalAmount = totalAmount.add(order.getTotalAmount());
            paidAmount = paidAmount.add(order.getPaidAmount());
        }

        LocalDateTime now = LocalDateTime.now();
        session.setStatus(TableSessionStatusEnum.SETTLING.getCode());
        session.setSettleTime(now);
        session.setTotalAmount(totalAmount);
        session.setPaidAmount(paidAmount);
        session.setOrderCount(orders.size());
        session.setUpdater("");
        session.setUpdateTime(now);
        sessionMapper.updateById(session);
        return session;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderTableSessionDO closeSession(String sessionNo) {
        OrderTableSessionDO session = getBySessionNo(sessionNo);
        if (session == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.TABLE_SESSION_NOT_FOUND);
        }

        String currentStatus = session.getStatus();
        if (TableSessionStatusEnum.CLOSED.getCode().equals(currentStatus)) {
            throw new OrderBusinessException(OrderErrorCodeConstants.TABLE_SESSION_ALREADY_CLOSED);
        }
        if (!TableSessionStatusEnum.SETTLING.getCode().equals(currentStatus)) {
            throw new OrderBusinessException(OrderErrorCodeConstants.TABLE_SESSION_INVALID,
                    "cannot close from status=" + currentStatus + ", must be SETTLING");
        }

        LocalDateTime now = LocalDateTime.now();
        session.setStatus(TableSessionStatusEnum.CLOSED.getCode());
        session.setCloseTime(now);
        session.setUpdater("");
        session.setUpdateTime(now);
        sessionMapper.updateById(session);
        return session;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderTableSessionDO validateForDineIn(Long tableSessionId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        if (tableSessionId == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.TABLE_SESSION_REQUIRED_FOR_DINE_IN);
        }

        OrderTableSessionDO session = sessionMapper.selectById(tableSessionId);
        if (session == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.TABLE_SESSION_NOT_FOUND);
        }

        // Verify tenant ownership
        if (!tenantId.equals(session.getTenantId())) {
            throw new OrderBusinessException(OrderErrorCodeConstants.TABLE_SESSION_NOT_FOUND);
        }

        String currentStatus = session.getStatus();
        if (TableSessionStatusEnum.CLOSED.getCode().equals(currentStatus)) {
            throw new OrderBusinessException(OrderErrorCodeConstants.TABLE_SESSION_ALREADY_CLOSED);
        }

        if (!TableSessionStatusEnum.OPEN.getCode().equals(currentStatus) &&
            !TableSessionStatusEnum.ORDERING.getCode().equals(currentStatus)) {
            throw new OrderBusinessException(OrderErrorCodeConstants.TABLE_SESSION_INVALID,
                    "status must be OPEN or ORDERING for DINE_IN, current=" + currentStatus);
        }

        // If OPEN, transition to ORDERING
        if (TableSessionStatusEnum.OPEN.getCode().equals(currentStatus)) {
            LocalDateTime now = LocalDateTime.now();
            session.setStatus(TableSessionStatusEnum.ORDERING.getCode());
            session.setUpdater("");
            session.setUpdateTime(now);
            sessionMapper.updateById(session);
        }

        return session;
    }

    @Override
    public void validateNonDineIn(Long tableSessionId) {
        if (tableSessionId != null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.TABLE_SESSION_NOT_FOR_NON_DINE_IN);
        }
    }

    private String generateSessionNo(Long tenantId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuidSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "S" + tenantId + timestamp + uuidSuffix;
    }
}
