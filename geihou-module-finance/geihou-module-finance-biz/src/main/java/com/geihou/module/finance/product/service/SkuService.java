package com.geihou.module.finance.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.controller.admin.vo.*;
import com.geihou.module.finance.product.dal.dataobject.*;
import com.geihou.module.finance.product.dal.mapper.*;
import com.geihou.module.finance.product.enums.SkuStatusEnum;
import com.geihou.module.finance.product.enums.StockStrategyEnum;
import com.geihou.module.finance.product.framework.ProductBusinessException;
import com.geihou.module.finance.product.framework.ProductErrorCodeConstants;
import com.geihou.module.finance.product.mq.ProductEventPublisher;
import com.geihou.module.finance.product.mq.event.ProductStatusChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * SKU service for product SKU CRUD and status management.
 *
 * <p>Tenant isolation is enforced via MyBatis-Plus TenantLineInnerInterceptor
 * and explicit tenant_id from TenantContextHolder.
 * All price fields use BigDecimal (never double/float).
 * Status changes write to product_availability_log in the same transaction.
 */
@Service
public class SkuService {

    private final ProductSkuMapper skuMapper;
    private final ProductSpuMapper spuMapper;
    private final ProductAvailabilityLogMapper availabilityLogMapper;
    private final ProductEventPublisher productEventPublisher;

    // G1-02F: Combo linkage — injected via setter to avoid circular dependency
    // (ComboService depends on ProductSkuMapper, not on SkuService)
    private ComboService comboService;

    public SkuService(ProductSkuMapper skuMapper,
                      ProductSpuMapper spuMapper,
                      ProductAvailabilityLogMapper availabilityLogMapper,
                      ProductEventPublisher productEventPublisher) {
        this.skuMapper = skuMapper;
        this.spuMapper = spuMapper;
        this.availabilityLogMapper = availabilityLogMapper;
        this.productEventPublisher = productEventPublisher;
    }

    @Autowired
    public void setComboService(ComboService comboService) {
        this.comboService = comboService;
    }

    /**
     * Create a new SKU. Must belong to an existing SPU.
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createSku(SkuCreateReqVO reqVO) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // Validate SPU exists (AC-10: SKU must belong to an SPU)
        if (reqVO.getSpuId() == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SKU_MUST_HAVE_SPU);
        }
        ProductSpuDO spu = spuMapper.selectById(reqVO.getSpuId());
        if (spu == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SKU_MUST_HAVE_SPU);
        }

        // Validate stock strategy
        StockStrategyEnum stockStrategy = StockStrategyEnum.fromCode(reqVO.getStockStrategy());

        // Validate prices are BigDecimal and not null
        Objects.requireNonNull(reqVO.getListPrice(), "listPrice must not be null");
        Objects.requireNonNull(reqVO.getSellingPrice(), "sellingPrice must not be null");
        if (reqVO.getListPrice().signum() < 0) {
            throw new IllegalArgumentException("listPrice must be >= 0");
        }
        if (reqVO.getSellingPrice().signum() < 0) {
            throw new IllegalArgumentException("sellingPrice must be >= 0");
        }

        // Validate selling price does not exceed 110% of list price (AC-9 equivalent)
        validateSellingPriceLimit(reqVO.getSellingPrice(), reqVO.getListPrice());

        LocalDateTime now = LocalDateTime.now();
        ProductSkuDO sku = new ProductSkuDO();
        sku.setTenantId(tenantId);
        sku.setSpuId(reqVO.getSpuId());
        sku.setSkuCode(reqVO.getSkuCode());
        sku.setSkuName(reqVO.getSkuName());
        sku.setSpecAttributes(reqVO.getSpecAttributes());
        sku.setListPrice(reqVO.getListPrice());
        sku.setSellingPrice(reqVO.getSellingPrice());
        sku.setCostPrice(reqVO.getCostPrice());
        sku.setMemberPrice(reqVO.getMemberPrice());
        sku.setDailyLimit(reqVO.getDailyLimit());
        sku.setPerOrderLimit(reqVO.getPerOrderLimit());
        sku.setMinOrderQuantity(reqVO.getMinOrderQuantity() != null ? reqVO.getMinOrderQuantity() : 1);
        sku.setStockStrategy(stockStrategy.getCode());
        sku.setStatus(SkuStatusEnum.NEW.getCode());
        sku.setStatusReason("Initial creation");
        sku.setPrimaryImageUrl(reqVO.getPrimaryImageUrl());
        sku.setTotalSoldCount(0);
        sku.setCreator("");
        sku.setCreateTime(now);
        sku.setUpdater("");
        sku.setUpdateTime(now);
        sku.setDeleted(false);

        skuMapper.insert(sku);
        return sku.getId();
    }

    /**
     * Get SKU by ID.
     */
    public SkuRespVO getSku(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        ProductSkuDO sku = skuMapper.selectById(id);
        if (sku == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SKU_NOT_FOUND);
        }
        return toRespVO(sku);
    }

    /**
     * Get SKU DO by ID (internal use).
     */
    public ProductSkuDO getSkuDO(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        ProductSkuDO sku = skuMapper.selectById(id);
        if (sku == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SKU_NOT_FOUND);
        }
        return sku;
    }

    /**
     * Update SKU (non-price, non-status fields).
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSku(SkuUpdateReqVO reqVO) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        ProductSkuDO sku = skuMapper.selectById(reqVO.getId());
        if (sku == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SKU_NOT_FOUND);
        }

        if (reqVO.getSkuName() != null) {
            sku.setSkuName(reqVO.getSkuName());
        }
        if (reqVO.getSpecAttributes() != null) {
            sku.setSpecAttributes(reqVO.getSpecAttributes());
        }
        if (reqVO.getDailyLimit() != null) {
            sku.setDailyLimit(reqVO.getDailyLimit());
        }
        if (reqVO.getPerOrderLimit() != null) {
            sku.setPerOrderLimit(reqVO.getPerOrderLimit());
        }
        if (reqVO.getMinOrderQuantity() != null) {
            sku.setMinOrderQuantity(reqVO.getMinOrderQuantity());
        }
        if (reqVO.getPrimaryImageUrl() != null) {
            sku.setPrimaryImageUrl(reqVO.getPrimaryImageUrl());
        }

        sku.setUpdater("");
        sku.setUpdateTime(LocalDateTime.now());
        skuMapper.updateById(sku);
    }

    /**
     * Change SKU status. Writes availability log in the same transaction.
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeSkuStatus(Long skuId, String newStatusCode, String reason, Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // Validate reason
        if (reason == null || reason.length() < 5) {
            throw new ProductBusinessException(ProductErrorCodeConstants.STATUS_CHANGE_REASON_REQUIRED);
        }

        ProductSkuDO sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SKU_NOT_FOUND);
        }

        SkuStatusEnum oldStatus = SkuStatusEnum.fromCode(sku.getStatus());
        SkuStatusEnum newStatus = SkuStatusEnum.fromCode(newStatusCode);

        // Validate transition
        if (!oldStatus.canTransitionTo(newStatus)) {
            throw new ProductBusinessException(ProductErrorCodeConstants.INVALID_STATUS_TRANSITION);
        }

        // Update SKU status
        sku.setStatus(newStatus.getCode());
        sku.setStatusReason(reason);
        sku.setUpdater("");
        sku.setUpdateTime(LocalDateTime.now());
        skuMapper.updateById(sku);

        // Write availability log (same transaction)
        LocalDateTime now = LocalDateTime.now();
        ProductAvailabilityLogDO log = new ProductAvailabilityLogDO();
        log.setTenantId(tenantId);
        log.setTargetType("SKU");
        log.setTargetId(skuId);
        log.setOldStatus(oldStatus.getCode());
        log.setNewStatus(newStatus.getCode());
        log.setChangeReason(reason);
        log.setChangedByUserId(userId);
        log.setChangeTime(now);
        log.setCreateTime(now);
        availabilityLogMapper.insert(log);

        // G1-02F AC-10: SKU 下架→套餐 PAUSED 联动
        // When SKU is paused or deprecated, auto-pause ACTIVE combos containing this SKU.
        if (newStatus == SkuStatusEnum.PAUSED || newStatus == SkuStatusEnum.DEPRECATED) {
            if (comboService != null) {
                comboService.pauseActiveCombosContainingSku(skuId, reason, userId);
            }
        }

        // G1-02G: Publish product.status.changed event via after-commit publisher.
        ProductStatusChangedEvent event = new ProductStatusChangedEvent();
        event.setTenantId(tenantId);
        event.setTargetType("SKU");
        event.setTargetId(skuId);
        event.setOldStatus(oldStatus.getCode());
        event.setNewStatus(newStatus.getCode());
        event.setChangeReason(reason);
        event.setChangedByUserId(userId);
        event.setChangeTime(now);
        productEventPublisher.publishStatusChanged(event);
    }

    /**
     * Validate that selling price does not exceed 110% of list price.
     */
    static void validateSellingPriceLimit(BigDecimal sellingPrice, BigDecimal listPrice) {
        if (listPrice != null && listPrice.signum() > 0) {
            BigDecimal limit = listPrice.multiply(new BigDecimal("1.1"));
            if (sellingPrice.compareTo(limit) > 0) {
                throw new ProductBusinessException(ProductErrorCodeConstants.SELLING_PRICE_EXCEEDS_LIST);
            }
        }
    }

    private SkuRespVO toRespVO(ProductSkuDO sku) {
        SkuRespVO vo = new SkuRespVO();
        vo.setId(sku.getId());
        vo.setSpuId(sku.getSpuId());
        vo.setSkuCode(sku.getSkuCode());
        vo.setSkuName(sku.getSkuName());
        vo.setSpecAttributes(sku.getSpecAttributes());
        vo.setListPrice(sku.getListPrice());
        vo.setSellingPrice(sku.getSellingPrice());
        vo.setCostPrice(sku.getCostPrice());
        vo.setMemberPrice(sku.getMemberPrice());
        vo.setDailyLimit(sku.getDailyLimit());
        vo.setPerOrderLimit(sku.getPerOrderLimit());
        vo.setMinOrderQuantity(sku.getMinOrderQuantity());
        vo.setStockStrategy(sku.getStockStrategy());
        vo.setStatus(sku.getStatus());
        vo.setStatusReason(sku.getStatusReason());
        vo.setPrimaryImageUrl(sku.getPrimaryImageUrl());
        vo.setTotalSoldCount(sku.getTotalSoldCount());
        vo.setCreateTime(sku.getCreateTime());
        vo.setUpdateTime(sku.getUpdateTime());
        return vo;
    }
}
