package com.geihou.module.finance.order.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.order.dal.dataobject.OrderIdempotentDO;
import com.geihou.module.finance.order.dal.mapper.OrderIdempotentMapper;
import com.geihou.module.finance.order.enums.OrderIdempotentStatusEnum;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.framework.OrderErrorCodeConstants;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Idempotent service for order creation.
 *
 * <p>DB unique key (tenant_id, idempotent_key) provides idempotency fallback.
 * Redis SETNX layer to be added when infrastructure is ready (R-9).
 *
 * <p>Flow:
 * 1. tryInsert: Insert PROCESSING record. If DuplicateKeyException → existing key found.
 * 2. If existing record is SUCCESS → return existing order_id.
 * 3. If existing record is PROCESSING → throw IDEMPOTENT_REQUEST_PROCESSING.
 * 4. If existing record is FAILED → allow retry (delete + re-insert).
 * 5. markSuccess: Update record to SUCCESS with order_id.
 * 6. markFailed: Update record to FAILED.
 */
@Service
public class IdempotentService {

    private static final int IDEMPOTENT_EXPIRE_HOURS = 24;

    private final OrderIdempotentMapper idempotentMapper;

    public IdempotentService(OrderIdempotentMapper idempotentMapper) {
        this.idempotentMapper = idempotentMapper;
    }

    /**
     * Try to acquire idempotent key. Returns existing order_id if already SUCCESS.
     *
     * @param idempotentKey client-provided idempotent key
     * @return existing order_id if duplicate SUCCESS, null if newly acquired
     * @throws OrderBusinessException if duplicate PROCESSING
     */
    public Long tryAcquire(String idempotentKey) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        LocalDateTime now = LocalDateTime.now();
        OrderIdempotentDO record = new OrderIdempotentDO();
        record.setTenantId(tenantId);
        record.setIdempotentKey(idempotentKey);
        record.setStatus(OrderIdempotentStatusEnum.PROCESSING.getCode());
        record.setExpireTime(now.plusHours(IDEMPOTENT_EXPIRE_HOURS));
        record.setCreateTime(now);

        try {
            idempotentMapper.insert(record);
            return null; // newly acquired
        } catch (DuplicateKeyException e) {
            // Existing record found
            OrderIdempotentDO existing = idempotentMapper.selectOne(
                    OrderIdempotentDO::getTenantId, tenantId,
                    OrderIdempotentDO::getIdempotentKey, idempotentKey
            );
            if (existing == null) {
                // Race condition — retry
                return tryAcquire(idempotentKey);
            }

            String status = existing.getStatus();
            if (OrderIdempotentStatusEnum.SUCCESS.getCode().equals(status)) {
                return existing.getOrderId(); // Return existing order
            } else if (OrderIdempotentStatusEnum.PROCESSING.getCode().equals(status)) {
                throw new OrderBusinessException(OrderErrorCodeConstants.IDEMPOTENT_REQUEST_PROCESSING);
            } else {
                // FAILED — allow retry by updating to PROCESSING
                existing.setStatus(OrderIdempotentStatusEnum.PROCESSING.getCode());
                existing.setExpireTime(now.plusHours(IDEMPOTENT_EXPIRE_HOURS));
                idempotentMapper.updateById(existing);
                return null;
            }
        }
    }

    /**
     * Mark idempotent record as SUCCESS with the created order ID.
     */
    public void markSuccess(String idempotentKey, Long orderId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        OrderIdempotentDO existing = idempotentMapper.selectOne(
                OrderIdempotentDO::getTenantId, tenantId,
                OrderIdempotentDO::getIdempotentKey, idempotentKey
        );
        if (existing != null) {
            existing.setStatus(OrderIdempotentStatusEnum.SUCCESS.getCode());
            existing.setOrderId(orderId);
            idempotentMapper.updateById(existing);
        }
    }

    /**
     * Mark idempotent record as FAILED (allowing retry).
     */
    public void markFailed(String idempotentKey) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        OrderIdempotentDO existing = idempotentMapper.selectOne(
                OrderIdempotentDO::getTenantId, tenantId,
                OrderIdempotentDO::getIdempotentKey, idempotentKey
        );
        if (existing != null) {
            existing.setStatus(OrderIdempotentStatusEnum.FAILED.getCode());
            idempotentMapper.updateById(existing);
        }
    }
}
