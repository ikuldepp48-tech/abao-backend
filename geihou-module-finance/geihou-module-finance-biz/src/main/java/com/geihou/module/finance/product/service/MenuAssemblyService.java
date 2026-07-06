package com.geihou.module.finance.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.controller.app.vo.*;
import com.geihou.module.finance.product.dal.dataobject.*;
import com.geihou.module.finance.product.dal.mapper.*;
import com.geihou.module.finance.product.enums.ComboStatusEnum;
import com.geihou.module.finance.product.enums.SkuStatusEnum;
import com.geihou.module.finance.product.enums.SpuStatusEnum;
import com.geihou.module.finance.product.enums.SpuTypeEnum;
import com.geihou.module.finance.product.framework.ProductBusinessException;
import com.geihou.module.finance.product.framework.ProductErrorCodeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Menu assembly service for customer-facing menu (G1-02H).
 *
 * <p>Assembles the customer menu by directly querying the product data tables
 * within the same finance-biz module. Does NOT use ProductApi cross-service interface
 * (D-1: same-module direct service composition, not HTTP self-invocation).
 *
 * <p>Filtering rules:
 * <ul>
 *   <li>Categories: status=ACTIVE only</li>
 *   <li>SPUs: status=ACTIVE only, excluding RAW_MATERIAL and SEMI_FINISHED types</li>
 *   <li>SKUs: status IN (ACTIVE, SOLD_OUT) only</li>
 *   <li>Addon options: status IN (ACTIVE, SOLD_OUT) only; DISABLED never returned</li>
 *   <li>Combo items: only when product_combo.status=ACTIVE; DEPRECATED item SKUs excluded</li>
 * </ul>
 *
 * <p>storeId is accepted but store-level inventory filtering is not yet implemented (D-3).
 * A warn log is recorded on each call. Tenant isolation is enforced via
 * TenantLineInnerInterceptor and explicit TenantContextHolder checks.
 *
 * <p>All price fields use BigDecimal (never double/float).
 * No hard delete operations.
 */
@Service
public class MenuAssemblyService {

    private static final Logger log = LoggerFactory.getLogger(MenuAssemblyService.class);

    private final ProductCategoryMapper categoryMapper;
    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductAddonGroupMapper addonGroupMapper;
    private final ProductAddonOptionMapper addonOptionMapper;
    private final ProductComboMapper comboMapper;
    private final ProductComboItemMapper comboItemMapper;
    private final SpuAddonGroupService spuAddonGroupService;

    public MenuAssemblyService(ProductCategoryMapper categoryMapper,
                               ProductSpuMapper spuMapper,
                               ProductSkuMapper skuMapper,
                               ProductAddonGroupMapper addonGroupMapper,
                               ProductAddonOptionMapper addonOptionMapper,
                               ProductComboMapper comboMapper,
                               ProductComboItemMapper comboItemMapper,
                               SpuAddonGroupService spuAddonGroupService) {
        this.categoryMapper = categoryMapper;
        this.spuMapper = spuMapper;
        this.skuMapper = skuMapper;
        this.addonGroupMapper = addonGroupMapper;
        this.addonOptionMapper = addonOptionMapper;
        this.comboMapper = comboMapper;
        this.comboItemMapper = comboItemMapper;
        this.spuAddonGroupService = spuAddonGroupService;
    }

    /**
     * Assemble the full customer menu.
     *
     * @param storeId store ID (required by PRD contract, but store-level filtering not yet implemented)
     * @return MenuVO containing the category tree with SPUs
     */
    public MenuVO assembleMenu(Long storeId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // D-3: storeId accepted but store-level inventory filtering not yet implemented
        log.warn("Store-level inventory filtering not yet implemented, returning tenant-wide active menu. storeId={}", storeId);

        // Query ACTIVE categories (sorted by sortOrder ASC, id ASC)
        List<ProductCategoryDO> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<ProductCategoryDO>()
                        .eq(ProductCategoryDO::getStatus, "ACTIVE")
                        .orderByAsc(ProductCategoryDO::getSortOrder)
                        .orderByAsc(ProductCategoryDO::getId));

        // Query ACTIVE SPUs, excluding RAW_MATERIAL and SEMI_FINISHED
        List<ProductSpuDO> spus = spuMapper.selectList(
                new LambdaQueryWrapper<ProductSpuDO>()
                        .eq(ProductSpuDO::getStatus, SpuStatusEnum.ACTIVE.getCode())
                        .notIn(ProductSpuDO::getSpuType,
                                SpuTypeEnum.RAW_MATERIAL.getCode(),
                                SpuTypeEnum.SEMI_FINISHED.getCode())
                        .orderByAsc(ProductSpuDO::getSortOrder)
                        .orderByAsc(ProductSpuDO::getId));

        // Query SKUs with status IN (ACTIVE, SOLD_OUT) for the active SPUs
        Set<Long> spuIds = spus.stream().map(ProductSpuDO::getId).collect(Collectors.toSet());
        List<ProductSkuDO> allSkus = new ArrayList<>();
        if (!spuIds.isEmpty()) {
            allSkus = skuMapper.selectList(
                    new LambdaQueryWrapper<ProductSkuDO>()
                            .in(ProductSkuDO::getSpuId, spuIds)
                            .in(ProductSkuDO::getStatus,
                                    SkuStatusEnum.ACTIVE.getCode(),
                                    SkuStatusEnum.SOLD_OUT.getCode())
                            .orderByAsc(ProductSkuDO::getId));
        }

        // Group SKUs by SPU ID
        Map<Long, List<ProductSkuDO>> skusBySpu = allSkus.stream()
                .collect(Collectors.groupingBy(ProductSkuDO::getSpuId));

        // Build MenuSpuVO list
        List<MenuSpuVO> menuSpus = spus.stream().map(spu -> {
            MenuSpuVO vo = toMenuSpuVO(spu);
            List<ProductSkuDO> spuSkus = skusBySpu.getOrDefault(spu.getId(), Collections.emptyList());
            vo.setSkus(spuSkus.stream().map(this::toMenuSkuVO).collect(Collectors.toList()));
            // Compute min/max selling price from ACTIVE SKUs only
            List<ProductSkuDO> activeSkus = spuSkus.stream()
                    .filter(s -> SkuStatusEnum.ACTIVE.getCode().equals(s.getStatus()))
                    .collect(Collectors.toList());
            if (!activeSkus.isEmpty()) {
                BigDecimal minPrice = activeSkus.stream()
                        .map(ProductSkuDO::getSellingPrice)
                        .min(BigDecimal::compareTo)
                        .orElse(null);
                BigDecimal maxPrice = activeSkus.stream()
                        .map(ProductSkuDO::getSellingPrice)
                        .max(BigDecimal::compareTo)
                        .orElse(null);
                vo.setMinSellingPrice(minPrice);
                vo.setMaxSellingPrice(maxPrice);
            }
            return vo;
        }).collect(Collectors.toList());

        // Group SPUs by category ID (pre-build spuId -> categoryId map to avoid O(n^2) scan)
        Map<Long, Long> spuIdToCategoryId = spus.stream()
                .collect(Collectors.toMap(ProductSpuDO::getId, ProductSpuDO::getCategoryId, (a, b) -> a));
        Map<Long, List<MenuSpuVO>> spusByCategory = menuSpus.stream()
                .collect(Collectors.groupingBy(spu -> spuIdToCategoryId.get(spu.getId())));

        // Build category tree
        List<MenuCategoryVO> categoryTree = buildCategoryTree(categories, spusByCategory);

        // Assemble MenuVO
        MenuVO menuVO = new MenuVO();
        menuVO.setStoreId(storeId);
        menuVO.setCategories(categoryTree);
        menuVO.setMenuSnapshotTime(LocalDateTime.now());
        return menuVO;
    }

    /**
     * Assemble SPU detail for customer view.
     *
     * @param spuId SPU ID
     * @return MenuSpuDetailVO containing SKU list, addon groups, and combo items
     * @throws ProductBusinessException if SPU not found or not ACTIVE
     */
    public MenuSpuDetailVO assembleSpuDetail(Long spuId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // Get SPU
        ProductSpuDO spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.MENU_SPU_NOT_FOUND);
        }

        // Check SPU is ACTIVE
        if (!SpuStatusEnum.ACTIVE.getCode().equals(spu.getStatus())) {
            throw new ProductBusinessException(ProductErrorCodeConstants.MENU_SPU_NOT_ACTIVE);
        }

        // Reject RAW_MATERIAL and SEMI_FINISHED types (matching menu list visibility rules)
        if (SpuTypeEnum.RAW_MATERIAL.getCode().equals(spu.getSpuType())
                || SpuTypeEnum.SEMI_FINISHED.getCode().equals(spu.getSpuType())) {
            throw new ProductBusinessException(ProductErrorCodeConstants.MENU_SPU_TYPE_NOT_VISIBLE);
        }

        // Get category info
        ProductCategoryDO category = categoryMapper.selectById(spu.getCategoryId());
        String categoryName = category != null ? category.getCategoryName() : null;

        // Get SKUs (ACTIVE + SOLD_OUT)
        List<ProductSkuDO> skus = skuMapper.selectList(
                new LambdaQueryWrapper<ProductSkuDO>()
                        .eq(ProductSkuDO::getSpuId, spuId)
                        .in(ProductSkuDO::getStatus,
                                SkuStatusEnum.ACTIVE.getCode(),
                                SkuStatusEnum.SOLD_OUT.getCode())
                        .orderByAsc(ProductSkuDO::getId));

        // Get addon groups via SpuAddonGroupService mapping
        List<Long> addonGroupIds = spuAddonGroupService.getAddonGroupIdsBySpu(spuId);
        List<MenuAddonGroupVO> addonGroups = new ArrayList<>();
        for (Long groupId : addonGroupIds) {
            ProductAddonGroupDO group = addonGroupMapper.selectById(groupId);
            if (group == null) {
                continue;
            }
            // Get options (ACTIVE + SOLD_OUT only, sorted by sortOrder ASC, id ASC)
            List<ProductAddonOptionDO> options = addonOptionMapper.selectList(
                    new LambdaQueryWrapper<ProductAddonOptionDO>()
                            .eq(ProductAddonOptionDO::getAddonGroupId, groupId)
                            .in(ProductAddonOptionDO::getStatus,
                                    "ACTIVE",
                                    "SOLD_OUT")
                            .orderByAsc(ProductAddonOptionDO::getSortOrder)
                            .orderByAsc(ProductAddonOptionDO::getId));
            MenuAddonGroupVO groupVO = toMenuAddonGroupVO(group);
            groupVO.setOptions(options.stream().map(this::toMenuAddonOptionVO).collect(Collectors.toList()));
            addonGroups.add(groupVO);
        }

        // Get combo items if spuType=COMBO
        List<MenuComboItemVO> comboItems = new ArrayList<>();
        if (SpuTypeEnum.COMBO.getCode().equals(spu.getSpuType())) {
            comboItems = buildComboItems(skus);
        }

        // Build MenuSpuDetailVO
        MenuSpuDetailVO detail = new MenuSpuDetailVO();
        detail.setId(spu.getId());
        detail.setSpuName(spu.getSpuName());
        detail.setSpuShortName(spu.getSpuShortName());
        detail.setPrimaryImageUrl(spu.getPrimaryImageUrl());
        detail.setDescription(spu.getDescription());
        detail.setIsRecommended(spu.getIsRecommended());
        detail.setIsNewArrival(spu.getIsNewArrival());
        detail.setSortOrder(spu.getSortOrder());
        detail.setSpuType(spu.getSpuType());
        detail.setCategoryId(spu.getCategoryId());
        detail.setCategoryName(categoryName);
        detail.setSkus(skus.stream().map(this::toMenuSkuVO).collect(Collectors.toList()));
        detail.setAddonGroups(addonGroups);
        detail.setComboItems(comboItems);
        return detail;
    }

    // ===== Private helpers =====

    /**
     * Build combo items for a COMBO SPU.
     * Finds the combo linked to the SPU's SKUs, checks combo.status=ACTIVE,
     * and returns combo items (excluding DEPRECATED item SKUs).
     */
    private List<MenuComboItemVO> buildComboItems(List<ProductSkuDO> spuSkus) {
        if (spuSkus.isEmpty()) {
            return Collections.emptyList();
        }

        // Find combo by comboSkuId (one of the SPU's SKU IDs)
        List<Long> skuIds = spuSkus.stream().map(ProductSkuDO::getId).collect(Collectors.toList());
        ProductComboDO combo = null;
        for (Long skuId : skuIds) {
            combo = comboMapper.selectOne(
                    new LambdaQueryWrapper<ProductComboDO>()
                            .eq(ProductComboDO::getComboSkuId, skuId));
            if (combo != null) {
                break;
            }
        }

        if (combo == null || !ComboStatusEnum.ACTIVE.getCode().equals(combo.getStatus())) {
            // PAUSED/DEPRECATED combo → empty list
            return Collections.emptyList();
        }

        // Get combo items
        List<ProductComboItemDO> items = comboItemMapper.selectList(
                new LambdaQueryWrapper<ProductComboItemDO>()
                        .eq(ProductComboItemDO::getComboId, combo.getId())
                        .orderByAsc(ProductComboItemDO::getId));

        List<MenuComboItemVO> result = new ArrayList<>();
        for (ProductComboItemDO item : items) {
            // Look up item SKU, skip if DEPRECATED
            ProductSkuDO itemSku = skuMapper.selectById(item.getItemSkuId());
            if (itemSku == null || SkuStatusEnum.DEPRECATED.getCode().equals(itemSku.getStatus())) {
                continue;
            }
            MenuComboItemVO vo = new MenuComboItemVO();
            vo.setItemSkuId(item.getItemSkuId());
            vo.setItemSkuName(itemSku.getSkuName());
            vo.setQuantity(item.getQuantity());
            vo.setIsOptional(item.getIsOptional());
            vo.setAlternativeGroup(item.getAlternativeGroup());
            result.add(vo);
        }
        return result;
    }

    /**
     * Build category tree from flat list, attaching SPUs to categories.
     */
    private List<MenuCategoryVO> buildCategoryTree(List<ProductCategoryDO> categories,
                                                    Map<Long, List<MenuSpuVO>> spusByCategory) {
        Map<Long, MenuCategoryVO> voMap = new HashMap<>();
        List<MenuCategoryVO> roots = new ArrayList<>();

        // First pass: create all VOs
        for (ProductCategoryDO category : categories) {
            MenuCategoryVO vo = toMenuCategoryVO(category);
            vo.getSpus().addAll(spusByCategory.getOrDefault(category.getId(), Collections.emptyList()));
            voMap.put(vo.getId(), vo);
        }

        // Second pass: build tree
        for (ProductCategoryDO category : categories) {
            MenuCategoryVO vo = voMap.get(category.getId());
            if (category.getParentCategoryId() == null) {
                roots.add(vo);
            } else {
                MenuCategoryVO parent = voMap.get(category.getParentCategoryId());
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    // Parent not found (possibly not ACTIVE), treat as root
                    roots.add(vo);
                }
            }
        }

        return roots;
    }

    private MenuSpuVO toMenuSpuVO(ProductSpuDO spu) {
        MenuSpuVO vo = new MenuSpuVO();
        vo.setId(spu.getId());
        vo.setSpuName(spu.getSpuName());
        vo.setSpuShortName(spu.getSpuShortName());
        vo.setPrimaryImageUrl(spu.getPrimaryImageUrl());
        vo.setDescription(spu.getDescription());
        vo.setIsRecommended(spu.getIsRecommended());
        vo.setIsNewArrival(spu.getIsNewArrival());
        vo.setSortOrder(spu.getSortOrder());
        vo.setSpuType(spu.getSpuType());
        return vo;
    }

    private MenuSkuVO toMenuSkuVO(ProductSkuDO sku) {
        MenuSkuVO vo = new MenuSkuVO();
        vo.setId(sku.getId());
        vo.setSkuName(sku.getSkuName());
        vo.setSpecAttributes(sku.getSpecAttributes());
        vo.setListPrice(sku.getListPrice());
        vo.setSellingPrice(sku.getSellingPrice());
        vo.setMemberPrice(sku.getMemberPrice());
        vo.setPrimaryImageUrl(sku.getPrimaryImageUrl());
        vo.setStatus(sku.getStatus());
        return vo;
    }

    private MenuCategoryVO toMenuCategoryVO(ProductCategoryDO category) {
        MenuCategoryVO vo = new MenuCategoryVO();
        vo.setId(category.getId());
        vo.setCategoryName(category.getCategoryName());
        vo.setSortOrder(category.getSortOrder());
        vo.setIcon(category.getIcon());
        return vo;
    }

    private MenuAddonGroupVO toMenuAddonGroupVO(ProductAddonGroupDO group) {
        MenuAddonGroupVO vo = new MenuAddonGroupVO();
        vo.setGroupId(group.getId());
        vo.setGroupName(group.getGroupName());
        vo.setSelectMin(group.getSelectMin());
        vo.setSelectMax(group.getSelectMax());
        vo.setIsRequired(group.getIsRequired());
        vo.setSortOrder(group.getSortOrder());
        return vo;
    }

    private MenuAddonOptionVO toMenuAddonOptionVO(ProductAddonOptionDO option) {
        MenuAddonOptionVO vo = new MenuAddonOptionVO();
        vo.setOptionId(option.getId());
        vo.setOptionName(option.getOptionName());
        vo.setExtraPrice(option.getExtraPrice());
        vo.setSortOrder(option.getSortOrder());
        vo.setStatus(option.getStatus());
        return vo;
    }
}
