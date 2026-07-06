package com.geihou.module.finance.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.common.pojo.PageResult;
import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.controller.admin.vo.*;
import com.geihou.module.finance.product.dal.dataobject.*;
import com.geihou.module.finance.product.dal.mapper.*;
import com.geihou.module.finance.product.enums.SkuStatusEnum;
import com.geihou.module.finance.product.enums.SpuStatusEnum;
import com.geihou.module.finance.product.enums.SpuTypeEnum;
import com.geihou.module.finance.product.framework.ProductBusinessException;
import com.geihou.module.finance.product.framework.ProductErrorCodeConstants;
import com.geihou.module.finance.product.mq.ProductEventPublisher;
import com.geihou.module.finance.product.mq.event.ProductStatusChangedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SPU service for product SPU CRUD and status management.
 *
 * <p>Tenant isolation is enforced via MyBatis-Plus TenantLineInnerInterceptor
 * and explicit tenant_id from TenantContextHolder.
 * Soft delete is used (no hard delete).
 * Status changes write to product_availability_log in the same transaction.
 */
@Service
public class SpuService {

    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductAvailabilityLogMapper availabilityLogMapper;
    private final ProductEventPublisher productEventPublisher;

    public SpuService(ProductSpuMapper spuMapper,
                      ProductSkuMapper skuMapper,
                      ProductCategoryMapper categoryMapper,
                      ProductAvailabilityLogMapper availabilityLogMapper,
                      ProductEventPublisher productEventPublisher) {
        this.spuMapper = spuMapper;
        this.skuMapper = skuMapper;
        this.categoryMapper = categoryMapper;
        this.availabilityLogMapper = availabilityLogMapper;
        this.productEventPublisher = productEventPublisher;
    }

    /**
     * Create a new SPU.
     *
     * @param reqVO creation request
     * @return new SPU ID
     * @throws ErrorCode if spu_code is duplicated, category not found, or spu_type invalid
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createSpu(SpuCreateReqVO reqVO) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // Validate spu_code uniqueness within tenant
        ProductSpuDO existing = spuMapper.selectOne(ProductSpuDO::getSpuCode, reqVO.getSpuCode());
        if (existing != null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SPU_CODE_DUPLICATED);
        }

        // Validate category exists
        ProductCategoryDO category = categoryMapper.selectById(reqVO.getCategoryId());
        if (category == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.CATEGORY_NOT_FOUND);
        }

        // Validate spu_type
        SpuTypeEnum spuType = SpuTypeEnum.fromCode(reqVO.getSpuType());

        // Validate status enum default
        SpuStatusEnum status = SpuStatusEnum.NEW;

        LocalDateTime now = LocalDateTime.now();
        ProductSpuDO spu = new ProductSpuDO();
        spu.setTenantId(tenantId);
        spu.setSpuCode(reqVO.getSpuCode());
        spu.setSpuName(reqVO.getSpuName());
        spu.setSpuShortName(reqVO.getSpuShortName());
        spu.setCategoryId(reqVO.getCategoryId());
        spu.setSpuType(spuType.getCode());
        spu.setPrimaryImageUrl(reqVO.getPrimaryImageUrl());
        spu.setDescription(reqVO.getDescription());
        spu.setIsRecommended(reqVO.getIsRecommended() != null ? reqVO.getIsRecommended() : false);
        spu.setIsNewArrival(reqVO.getIsNewArrival() != null ? reqVO.getIsNewArrival() : false);
        spu.setSortOrder(reqVO.getSortOrder() != null ? reqVO.getSortOrder() : 0);
        spu.setTotalSoldCount(0);
        spu.setStatus(status.getCode());
        spu.setCreator("");
        spu.setCreateTime(now);
        spu.setUpdater("");
        spu.setUpdateTime(now);
        spu.setDeleted(false);

        spuMapper.insert(spu);
        return spu.getId();
    }

    /**
     * Get SPU by ID.
     */
    public SpuRespVO getSpu(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        ProductSpuDO spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SPU_NOT_FOUND);
        }
        return toRespVO(spu);
    }

    /**
     * Get SPU DO by ID (internal use).
     */
    public ProductSpuDO getSpuDO(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        ProductSpuDO spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SPU_NOT_FOUND);
        }
        return spu;
    }

    /**
     * Page query SPU with optional filters.
     */
    public PageResult<SpuRespVO> pageSpu(SpuPageReqVO reqVO) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        LambdaQueryWrapper<ProductSpuDO> wrapper = new LambdaQueryWrapper<ProductSpuDO>()
                .orderByDesc(ProductSpuDO::getId);

        if (reqVO.getSpuCode() != null && !reqVO.getSpuCode().isBlank()) {
            wrapper.like(ProductSpuDO::getSpuCode, reqVO.getSpuCode());
        }
        if (reqVO.getSpuName() != null && !reqVO.getSpuName().isBlank()) {
            wrapper.like(ProductSpuDO::getSpuName, reqVO.getSpuName());
        }
        if (reqVO.getCategoryId() != null) {
            wrapper.eq(ProductSpuDO::getCategoryId, reqVO.getCategoryId());
        }
        if (reqVO.getStatus() != null && !reqVO.getStatus().isBlank()) {
            wrapper.eq(ProductSpuDO::getStatus, reqVO.getStatus());
        }
        if (reqVO.getSpuType() != null && !reqVO.getSpuType().isBlank()) {
            wrapper.eq(ProductSpuDO::getSpuType, reqVO.getSpuType());
        }

        PageResult<ProductSpuDO> pageResult = spuMapper.selectPage(
                reqVO.getPageNo(), reqVO.getPageSize(), wrapper);

        List<SpuRespVO> voList = pageResult.getList().stream()
                .map(this::toRespVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, pageResult.getTotal(), pageResult.getPageNo(), pageResult.getPageSize());
    }

    /**
     * Update SPU (non-status, non-code fields).
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSpu(SpuUpdateReqVO reqVO) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        ProductSpuDO spu = spuMapper.selectById(reqVO.getId());
        if (spu == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SPU_NOT_FOUND);
        }

        // Validate category if changed
        if (reqVO.getCategoryId() != null && !reqVO.getCategoryId().equals(spu.getCategoryId())) {
            ProductCategoryDO category = categoryMapper.selectById(reqVO.getCategoryId());
            if (category == null) {
                throw new ProductBusinessException(ProductErrorCodeConstants.CATEGORY_NOT_FOUND);
            }
            spu.setCategoryId(reqVO.getCategoryId());
        }

        if (reqVO.getSpuName() != null) {
            spu.setSpuName(reqVO.getSpuName());
        }
        if (reqVO.getSpuShortName() != null) {
            spu.setSpuShortName(reqVO.getSpuShortName());
        }
        if (reqVO.getPrimaryImageUrl() != null) {
            spu.setPrimaryImageUrl(reqVO.getPrimaryImageUrl());
        }
        if (reqVO.getDescription() != null) {
            spu.setDescription(reqVO.getDescription());
        }
        if (reqVO.getIsRecommended() != null) {
            spu.setIsRecommended(reqVO.getIsRecommended());
        }
        if (reqVO.getIsNewArrival() != null) {
            spu.setIsNewArrival(reqVO.getIsNewArrival());
        }
        if (reqVO.getSortOrder() != null) {
            spu.setSortOrder(reqVO.getSortOrder());
        }

        spu.setUpdater("");
        spu.setUpdateTime(LocalDateTime.now());

        spuMapper.updateById(spu);
    }

    /**
     * Soft delete SPU. SPU must be DEPRECATED and have no active SKUs.
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSpu(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        ProductSpuDO spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SPU_NOT_FOUND);
        }

        // Check SPU is DEPRECATED before soft delete
        if (!SpuStatusEnum.DEPRECATED.getCode().equals(spu.getStatus())) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SPU_MUST_BE_DEPRECATED_BEFORE_DELETE);
        }

        // Check no active SKUs
        List<ProductSkuDO> activeSkus = skuMapper.selectList(
                ProductSkuDO::getSpuId, id);
        boolean hasActiveSkus = activeSkus.stream()
                .anyMatch(sku -> !safeSkuStatus(sku.getStatus()).equals(SkuStatusEnum.DEPRECATED)
                        && !Boolean.TRUE.equals(sku.getDeleted()));
        if (hasActiveSkus) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SPU_HAS_ACTIVE_SKUS);
        }

        // Soft delete via updateById with deleted=true (MyBatis-Plus @TableLogic handles this)
        spuMapper.deleteById(id);
    }

    /**
     * Change SPU status. Writes availability log in the same transaction.
     *
     * @param spuId    SPU ID
     * @param reqVO    status change request (newStatus + reason)
     * @param userId   user performing the change
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeSpuStatus(Long spuId, SpuStatusChangeReqVO reqVO, Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // Validate reason
        if (reqVO.getReason() == null || reqVO.getReason().length() < 5) {
            throw new ProductBusinessException(ProductErrorCodeConstants.STATUS_CHANGE_REASON_REQUIRED);
        }

        ProductSpuDO spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SPU_NOT_FOUND);
        }

        SpuStatusEnum oldStatus = SpuStatusEnum.fromCode(spu.getStatus());
        SpuStatusEnum newStatus = SpuStatusEnum.fromCode(reqVO.getNewStatus());

        // Validate transition
        if (!oldStatus.canTransitionTo(newStatus)) {
            throw new ProductBusinessException(ProductErrorCodeConstants.INVALID_STATUS_TRANSITION);
        }

        // Update SPU status
        spu.setStatus(newStatus.getCode());
        spu.setUpdater("");
        spu.setUpdateTime(LocalDateTime.now());
        spuMapper.updateById(spu);

        // Write availability log (same transaction)
        LocalDateTime now = LocalDateTime.now();
        ProductAvailabilityLogDO log = new ProductAvailabilityLogDO();
        log.setTenantId(tenantId);
        log.setTargetType("SPU");
        log.setTargetId(spuId);
        log.setOldStatus(oldStatus.getCode());
        log.setNewStatus(newStatus.getCode());
        log.setChangeReason(reqVO.getReason());
        log.setChangedByUserId(userId);
        log.setChangeTime(now);
        log.setCreateTime(now);
        availabilityLogMapper.insert(log);

        // G1-02G: Publish product.status.changed event via after-commit publisher.
        ProductStatusChangedEvent event = new ProductStatusChangedEvent();
        event.setTenantId(tenantId);
        event.setTargetType("SPU");
        event.setTargetId(spuId);
        event.setOldStatus(oldStatus.getCode());
        event.setNewStatus(newStatus.getCode());
        event.setChangeReason(reqVO.getReason());
        event.setChangedByUserId(userId);
        event.setChangeTime(now);
        productEventPublisher.publishStatusChanged(event);
    }

    private SpuRespVO toRespVO(ProductSpuDO spu) {
        SpuRespVO vo = new SpuRespVO();
        vo.setId(spu.getId());
        vo.setSpuCode(spu.getSpuCode());
        vo.setSpuName(spu.getSpuName());
        vo.setSpuShortName(spu.getSpuShortName());
        vo.setCategoryId(spu.getCategoryId());
        vo.setSpuType(spu.getSpuType());
        vo.setPrimaryImageUrl(spu.getPrimaryImageUrl());
        vo.setDescription(spu.getDescription());
        vo.setIsRecommended(spu.getIsRecommended());
        vo.setIsNewArrival(spu.getIsNewArrival());
        vo.setSortOrder(spu.getSortOrder());
        vo.setTotalSoldCount(spu.getTotalSoldCount());
        vo.setStatus(spu.getStatus());
        vo.setCreateTime(spu.getCreateTime());
        vo.setUpdateTime(spu.getUpdateTime());
        return vo;
    }

    private static SkuStatusEnum safeSkuStatus(String code) {
        if (code == null) {
            return SkuStatusEnum.NEW;
        }
        try {
            return SkuStatusEnum.fromCode(code);
        } catch (IllegalArgumentException e) {
            return SkuStatusEnum.NEW;
        }
    }
}
