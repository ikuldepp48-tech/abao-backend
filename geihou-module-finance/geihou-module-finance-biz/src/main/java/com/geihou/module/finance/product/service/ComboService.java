package com.geihou.module.finance.product.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.controller.admin.vo.*;
import com.geihou.module.finance.product.dal.dataobject.*;
import com.geihou.module.finance.product.dal.mapper.*;
import com.geihou.module.finance.product.enums.ComboStatusEnum;
import com.geihou.module.finance.product.enums.SkuStatusEnum;
import com.geihou.module.finance.product.framework.ProductBusinessException;
import com.geihou.module.finance.product.framework.ProductErrorCodeConstants;
import com.geihou.module.finance.product.mq.ProductEventPublisher;
import com.geihou.module.finance.product.mq.event.ProductStatusChangedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Combo service for 套餐 CRUD, price validation, status management, and SKU linkage.
 *
 * <p>Tenant isolation is enforced via MyBatis-Plus TenantLineInnerInterceptor
 * and explicit tenant_id from TenantContextHolder.
 * Soft delete is used (no hard delete).
 * All combo_price fields use BigDecimal (never double/float).
 * Combo status uses ENUM_COMBO_STATUS (ACTIVE/PAUSED/DEPRECATED).
 *
 * <p>Price validation (AC-6): combo_price < sum(item_sku.selling_price * quantity) * 0.9
 * → reject COMBO_PRICE_UNREASONABLE.
 * SKU linkage (AC-10): when SKU status changes to PAUSED/DEPRECATED, active combos
 * containing that SKU are auto-paused (called from SkuService.changeSkuStatus).
 */
@Service
public class ComboService {

    private final ProductComboMapper comboMapper;
    private final ProductComboItemMapper comboItemMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductAvailabilityLogMapper availabilityLogMapper;
    private final ProductEventPublisher productEventPublisher;

    public ComboService(ProductComboMapper comboMapper,
                        ProductComboItemMapper comboItemMapper,
                        ProductSkuMapper skuMapper,
                        ProductAvailabilityLogMapper availabilityLogMapper,
                        ProductEventPublisher productEventPublisher) {
        this.comboMapper = comboMapper;
        this.comboItemMapper = comboItemMapper;
        this.skuMapper = skuMapper;
        this.availabilityLogMapper = availabilityLogMapper;
        this.productEventPublisher = productEventPublisher;
    }

    // ===== Combo CRUD =====

    @Transactional(rollbackFor = Exception.class)
    public Long createCombo(ComboCreateReqVO reqVO) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        Objects.requireNonNull(reqVO.getComboPrice(), "comboPrice must not be null");
        if (reqVO.getComboPrice().signum() < 0) {
            throw new IllegalArgumentException("comboPrice must be >= 0");
        }

        // Validate combo_sku_id references an existing SKU in the same tenant
        ProductSkuDO comboSku = skuMapper.selectById(reqVO.getComboSkuId());
        if (comboSku == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SKU_NOT_FOUND);
        }

        // Validate combo_sku_id is not already linked to another combo (uk_combo_sku)
        ProductComboDO existingCombo = comboMapper.selectOne(
                ProductComboDO::getComboSkuId, reqVO.getComboSkuId());
        if (existingCombo != null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.COMBO_SKU_ALREADY_LINKED);
        }

        // Validate initial items if provided
        List<ComboItemCreateReqVO> items = reqVO.getItems() != null ? reqVO.getItems() : new ArrayList<>();
        validateComboItems(items, null);

        // Validate combo price (AC-6)
        validateComboPrice(reqVO.getComboPrice(), items);

        LocalDateTime now = LocalDateTime.now();
        ProductComboDO combo = new ProductComboDO();
        combo.setTenantId(tenantId);
        combo.setComboSkuId(reqVO.getComboSkuId());
        combo.setComboName(reqVO.getComboName());
        combo.setComboPrice(reqVO.getComboPrice());
        combo.setEffectiveFrom(reqVO.getEffectiveFrom());
        combo.setEffectiveUntil(reqVO.getEffectiveUntil());
        combo.setStatus(ComboStatusEnum.ACTIVE.getCode());
        combo.setCreator("");
        combo.setCreateTime(now);
        combo.setUpdater("");
        combo.setUpdateTime(now);
        combo.setDeleted(false);

        comboMapper.insert(combo);

        // Insert initial combo items
        for (ComboItemCreateReqVO itemReq : items) {
            insertComboItem(combo.getId(), itemReq, tenantId, now);
        }

        return combo.getId();
    }

    public ComboRespVO getCombo(Long id) {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        ProductComboDO combo = comboMapper.selectById(id);
        if (combo == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.COMBO_NOT_FOUND);
        }
        return toComboRespVO(combo, listItems(id));
    }

    public List<ComboRespVO> listCombos() {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        List<ProductComboDO> combos = comboMapper.selectList();
        return combos.stream()
                .map(c -> toComboRespVO(c, listItems(c.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCombo(Long id, ComboUpdateReqVO reqVO) {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        ProductComboDO combo = comboMapper.selectById(id);
        if (combo == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.COMBO_NOT_FOUND);
        }

        // If combo_price is being updated, re-validate against current items
        if (reqVO.getComboPrice() != null) {
            if (reqVO.getComboPrice().signum() < 0) {
                throw new IllegalArgumentException("comboPrice must be >= 0");
            }
            List<ProductComboItemDO> existingItems = listItems(id);
            List<ComboItemCreateReqVO> itemReqs = existingItems.stream()
                    .map(this::toItemCreateReq)
                    .collect(Collectors.toList());
            validateComboPrice(reqVO.getComboPrice(), itemReqs);
            combo.setComboPrice(reqVO.getComboPrice());
        }

        if (reqVO.getComboName() != null) {
            combo.setComboName(reqVO.getComboName());
        }
        if (reqVO.getEffectiveFrom() != null) {
            combo.setEffectiveFrom(reqVO.getEffectiveFrom());
        }
        if (reqVO.getEffectiveUntil() != null) {
            combo.setEffectiveUntil(reqVO.getEffectiveUntil());
        }

        combo.setUpdater("");
        combo.setUpdateTime(LocalDateTime.now());
        comboMapper.updateById(combo);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCombo(Long id) {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        ProductComboDO combo = comboMapper.selectById(id);
        if (combo == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.COMBO_NOT_FOUND);
        }

        // Soft delete all items first
        List<ProductComboItemDO> items = listItems(id);
        for (ProductComboItemDO item : items) {
            comboItemMapper.deleteById(item.getId());
        }

        comboMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeComboStatus(Long id, ComboStatusChangeReqVO reqVO, Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        if (reqVO.getReason() == null || reqVO.getReason().length() < 5) {
            throw new ProductBusinessException(ProductErrorCodeConstants.STATUS_CHANGE_REASON_REQUIRED);
        }

        ProductComboDO combo = comboMapper.selectById(id);
        if (combo == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.COMBO_NOT_FOUND);
        }

        ComboStatusEnum oldStatus = ComboStatusEnum.fromCode(combo.getStatus());
        ComboStatusEnum newStatus = ComboStatusEnum.fromCode(reqVO.getNewStatus());

        if (!oldStatus.canTransitionTo(newStatus)) {
            throw new ProductBusinessException(ProductErrorCodeConstants.INVALID_STATUS_TRANSITION);
        }

        combo.setStatus(newStatus.getCode());
        combo.setUpdater("");
        combo.setUpdateTime(LocalDateTime.now());
        comboMapper.updateById(combo);

        // Write availability log (target_type=COMBO)
        LocalDateTime now = LocalDateTime.now();
        ProductAvailabilityLogDO log = new ProductAvailabilityLogDO();
        log.setTenantId(tenantId);
        log.setTargetType("COMBO");
        log.setTargetId(id);
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
        event.setTargetType("COMBO");
        event.setTargetId(id);
        event.setOldStatus(oldStatus.getCode());
        event.setNewStatus(newStatus.getCode());
        event.setChangeReason(reqVO.getReason());
        event.setChangedByUserId(userId);
        event.setChangeTime(now);
        productEventPublisher.publishStatusChanged(event);
    }

    // ===== Combo Item CRUD =====

    @Transactional(rollbackFor = Exception.class)
    public Long addComboItem(Long comboId, ComboItemCreateReqVO reqVO) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        ProductComboDO combo = comboMapper.selectById(comboId);
        if (combo == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.COMBO_NOT_FOUND);
        }

        validateComboItems(List.of(reqVO), comboId);

        LocalDateTime now = LocalDateTime.now();
        ProductComboItemDO item = insertComboItem(comboId, reqVO, tenantId, now);

        // Re-validate combo price after adding item
        List<ProductComboItemDO> allItems = listItems(comboId);
        List<ComboItemCreateReqVO> itemReqs = allItems.stream()
                .map(this::toItemCreateReq)
                .collect(Collectors.toList());
        validateComboPrice(combo.getComboPrice(), itemReqs);

        return item.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateComboItem(Long comboId, Long itemId, ComboItemUpdateReqVO reqVO) {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        ProductComboItemDO item = comboItemMapper.selectOne(
                ProductComboItemDO::getId, itemId,
                ProductComboItemDO::getComboId, comboId);
        if (item == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.COMBO_ITEM_NOT_FOUND);
        }

        if (reqVO.getQuantity() != null) {
            if (reqVO.getQuantity() <= 0) {
                throw new IllegalArgumentException("quantity must be > 0");
            }
            item.setQuantity(reqVO.getQuantity());
        }
        if (reqVO.getIsOptional() != null) {
            item.setIsOptional(reqVO.getIsOptional());
        }
        if (reqVO.getAlternativeGroup() != null) {
            item.setAlternativeGroup(reqVO.getAlternativeGroup());
        }

        item.setUpdater("");
        item.setUpdateTime(LocalDateTime.now());
        comboItemMapper.updateById(item);

        // Re-validate combo price after updating item
        ProductComboDO combo = comboMapper.selectById(comboId);
        if (combo != null) {
            List<ProductComboItemDO> allItems = listItems(comboId);
            List<ComboItemCreateReqVO> itemReqs = allItems.stream()
                    .map(this::toItemCreateReq)
                    .collect(Collectors.toList());
            validateComboPrice(combo.getComboPrice(), itemReqs);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteComboItem(Long comboId, Long itemId) {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        ProductComboItemDO item = comboItemMapper.selectOne(
                ProductComboItemDO::getId, itemId,
                ProductComboItemDO::getComboId, comboId);
        if (item == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.COMBO_ITEM_NOT_FOUND);
        }

        comboItemMapper.deleteById(itemId);
    }

    // ===== SKU Linkage (called from SkuService.changeSkuStatus) =====

    /**
     * When a SKU is paused or deprecated, find all ACTIVE combos that contain this SKU
     * and transition them to PAUSED. Writes availability_log with target_type=COMBO.
     *
     * <p>AC-10: SKU 下架→套餐 PAUSED 联动。
     * This method is called within the same transaction as SkuService.changeSkuStatus.
     *
     * @param skuId   the SKU being paused/deprecated
     * @param reason  the reason from the SKU status change
     * @param userId  the user performing the change
     */
    @Transactional(rollbackFor = Exception.class)
    public void pauseActiveCombosContainingSku(Long skuId, String reason, Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // Find all combo_items referencing this SKU
        List<ProductComboItemDO> items = comboItemMapper.selectList(
                ProductComboItemDO::getItemSkuId, skuId);

        Set<Long> comboIdsToPause = new HashSet<>();
        for (ProductComboItemDO item : items) {
            ProductComboDO combo = comboMapper.selectById(item.getComboId());
            if (combo != null && ComboStatusEnum.ACTIVE.getCode().equals(combo.getStatus())) {
                comboIdsToPause.add(combo.getId());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (Long comboId : comboIdsToPause) {
            ProductComboDO combo = comboMapper.selectById(comboId);
            if (combo == null) {
                continue;
            }
            String oldStatus = combo.getStatus();
            combo.setStatus(ComboStatusEnum.PAUSED.getCode());
            combo.setUpdater("");
            combo.setUpdateTime(now);
            comboMapper.updateById(combo);

            // Write availability log (target_type=COMBO)
            ProductAvailabilityLogDO log = new ProductAvailabilityLogDO();
            log.setTenantId(tenantId);
            log.setTargetType("COMBO");
            log.setTargetId(comboId);
            log.setOldStatus(oldStatus);
            log.setNewStatus(ComboStatusEnum.PAUSED.getCode());
            log.setChangeReason(reason);
            log.setChangedByUserId(userId);
            log.setChangeTime(now);
            log.setCreateTime(now);
            availabilityLogMapper.insert(log);
        }
    }

    // ===== Internal helpers =====

    private void validateComboItems(List<ComboItemCreateReqVO> items, Long comboId) {
        Set<Long> seenSkuIds = new HashSet<>();
        for (ComboItemCreateReqVO item : items) {
            if (item.getItemSkuId() == null) {
                throw new ProductBusinessException(ProductErrorCodeConstants.SKU_NOT_FOUND);
            }
            // AC-8: item_sku_id must reference existing SKU in same tenant with ACTIVE or SOLD_OUT status
            ProductSkuDO sku = skuMapper.selectById(item.getItemSkuId());
            if (sku == null) {
                throw new ProductBusinessException(ProductErrorCodeConstants.SKU_NOT_FOUND);
            }
            String skuStatus = sku.getStatus();
            if (!SkuStatusEnum.ACTIVE.getCode().equals(skuStatus)
                    && !SkuStatusEnum.SOLD_OUT.getCode().equals(skuStatus)) {
                throw new ProductBusinessException(ProductErrorCodeConstants.SKU_NOT_FOUND);
            }

            // AC-11: combo item uniqueness (combo_id, item_sku_id, deleted)
            if (!seenSkuIds.add(item.getItemSkuId())) {
                throw new ProductBusinessException(ProductErrorCodeConstants.COMBO_ITEM_DUPLICATE);
            }

            // Check against existing items if comboId is provided (update scenario)
            if (comboId != null) {
                ProductComboItemDO existing = comboItemMapper.selectOne(
                        ProductComboItemDO::getComboId, comboId,
                        ProductComboItemDO::getItemSkuId, item.getItemSkuId());
                if (existing != null) {
                    throw new ProductBusinessException(ProductErrorCodeConstants.COMBO_ITEM_DUPLICATE);
                }
            }

            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("quantity must be > 0");
            }
        }
    }

    /**
     * AC-6: combo_price must be >= sum(item_sku.selling_price * quantity) * 0.9.
     * If combo_price < sum * 0.9 → reject COMBO_PRICE_UNREASONABLE.
     */
    private void validateComboPrice(BigDecimal comboPrice, List<ComboItemCreateReqVO> items) {
        if (items == null || items.isEmpty()) {
            // No items — cannot validate price ratio; allow creation (items added later)
            return;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (ComboItemCreateReqVO item : items) {
            ProductSkuDO sku = skuMapper.selectById(item.getItemSkuId());
            if (sku == null) {
                continue;
            }
            BigDecimal qty = BigDecimal.valueOf(item.getQuantity());
            sum = sum.add(sku.getSellingPrice().multiply(qty));
        }
        if (sum.signum() <= 0) {
            return;
        }
        BigDecimal threshold = sum.multiply(new BigDecimal("0.9"));
        if (comboPrice.compareTo(threshold) < 0) {
            throw new ProductBusinessException(ProductErrorCodeConstants.COMBO_PRICE_UNREASONABLE,
                    "combo_price " + comboPrice + " < 90% of internal SKU sum " + sum
                            + " (threshold " + threshold + ")");
        }
    }

    private ProductComboItemDO insertComboItem(Long comboId, ComboItemCreateReqVO reqVO,
                                                Long tenantId, LocalDateTime now) {
        ProductComboItemDO item = new ProductComboItemDO();
        item.setTenantId(tenantId);
        item.setComboId(comboId);
        item.setItemSkuId(reqVO.getItemSkuId());
        item.setQuantity(reqVO.getQuantity());
        item.setIsOptional(reqVO.getIsOptional() != null ? reqVO.getIsOptional() : false);
        item.setAlternativeGroup(reqVO.getAlternativeGroup());
        item.setCreator("");
        item.setCreateTime(now);
        item.setUpdater("");
        item.setUpdateTime(now);
        item.setDeleted(false);
        comboItemMapper.insert(item);
        return item;
    }

    private List<ProductComboItemDO> listItems(Long comboId) {
        return comboItemMapper.selectList(
                ProductComboItemDO::getComboId, comboId);
    }

    private ComboItemCreateReqVO toItemCreateReq(ProductComboItemDO item) {
        ComboItemCreateReqVO req = new ComboItemCreateReqVO();
        req.setItemSkuId(item.getItemSkuId());
        req.setQuantity(item.getQuantity());
        req.setIsOptional(item.getIsOptional());
        req.setAlternativeGroup(item.getAlternativeGroup());
        return req;
    }

    private ComboRespVO toComboRespVO(ProductComboDO combo, List<ProductComboItemDO> items) {
        ComboRespVO vo = new ComboRespVO();
        vo.setId(combo.getId());
        vo.setComboSkuId(combo.getComboSkuId());
        vo.setComboName(combo.getComboName());
        vo.setComboPrice(combo.getComboPrice());
        vo.setEffectiveFrom(combo.getEffectiveFrom());
        vo.setEffectiveUntil(combo.getEffectiveUntil());
        vo.setStatus(combo.getStatus());
        vo.setCreateTime(combo.getCreateTime());
        vo.setUpdateTime(combo.getUpdateTime());
        vo.setItems(items.stream()
                .map(this::toItemRespVO)
                .collect(Collectors.toList()));
        return vo;
    }

    private ComboItemRespVO toItemRespVO(ProductComboItemDO item) {
        ComboItemRespVO vo = new ComboItemRespVO();
        vo.setId(item.getId());
        vo.setComboId(item.getComboId());
        vo.setItemSkuId(item.getItemSkuId());
        vo.setQuantity(item.getQuantity());
        vo.setIsOptional(item.getIsOptional());
        vo.setAlternativeGroup(item.getAlternativeGroup());
        vo.setCreateTime(item.getCreateTime());
        vo.setUpdateTime(item.getUpdateTime());
        return vo;
    }
}
