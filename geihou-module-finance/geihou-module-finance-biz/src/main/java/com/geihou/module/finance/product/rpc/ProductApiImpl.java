package com.geihou.module.finance.product.rpc;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.product.ProductApi;
import com.geihou.module.finance.api.product.dto.*;
import com.geihou.module.finance.product.dal.dataobject.*;
import com.geihou.module.finance.product.dal.mapper.*;
import com.geihou.module.finance.product.enums.ComboStatusEnum;
import com.geihou.module.finance.product.enums.SkuStatusEnum;
import com.geihou.module.finance.product.framework.ProductBusinessException;
import com.geihou.module.finance.product.framework.ProductErrorCodeConstants;
import com.geihou.module.finance.product.service.SpuAddonGroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * HTTP implementation of {@link ProductApi}.
 *
 * <p>Exposed at {@code /rpc-api/finance/product} as an internal RPC endpoint
 * (not admin-api or app-api). Gateway layer should restrict external access.
 *
 * <p>Tenant context is verified at each method entry via {@code TenantContextHolder}.
 * No authentication is implemented in this slice — gateway protection is required.
 */
@RestController
@RequestMapping("/rpc-api/finance/product")
public class ProductApiImpl implements ProductApi {

    private static final Logger log = LoggerFactory.getLogger(ProductApiImpl.class);

    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductComboMapper comboMapper;
    private final ProductComboItemMapper comboItemMapper;
    private final ProductAddonGroupMapper addonGroupMapper;
    private final ProductAddonOptionMapper addonOptionMapper;
    private final SpuAddonGroupService spuAddonGroupService;

    public ProductApiImpl(ProductSpuMapper spuMapper,
                          ProductSkuMapper skuMapper,
                          ProductComboMapper comboMapper,
                          ProductComboItemMapper comboItemMapper,
                          ProductAddonGroupMapper addonGroupMapper,
                          ProductAddonOptionMapper addonOptionMapper,
                          SpuAddonGroupService spuAddonGroupService) {
        this.spuMapper = spuMapper;
        this.skuMapper = skuMapper;
        this.comboMapper = comboMapper;
        this.comboItemMapper = comboItemMapper;
        this.addonGroupMapper = addonGroupMapper;
        this.addonOptionMapper = addonOptionMapper;
        this.spuAddonGroupService = spuAddonGroupService;
    }

    @Override
    @GetMapping("/spu/{spuId}")
    public SpuRespDTO getSpu(Long spuId) {
        requireTenantContext();
        ProductSpuDO spu = spuMapper.selectById(spuId);
        if (spu == null) {
            return null;
        }
        return toSpuRespDTO(spu);
    }

    @Override
    @GetMapping("/sku/{skuId}")
    public SkuRespDTO getSku(Long skuId) {
        requireTenantContext();
        ProductSkuDO sku = skuMapper.selectById(skuId);
        if (sku == null) {
            return null;
        }
        return toSkuRespDTO(sku);
    }

    @Override
    @PostMapping("/sku/batch")
    public Map<Long, SkuRespDTO> batchGetSkus(List<Long> skuIds) {
        requireTenantContext();
        if (skuIds == null || skuIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, SkuRespDTO> result = new HashMap<>();
        for (Long skuId : skuIds) {
            ProductSkuDO sku = skuMapper.selectById(skuId);
            if (sku != null) {
                result.put(skuId, toSkuRespDTO(sku));
            }
        }
        return result;
    }

    @Override
    @GetMapping("/sku/{skuId}/availability")
    public SkuAvailabilityRespDTO checkAvailability(Long skuId, Integer quantity) {
        requireTenantContext();
        SkuAvailabilityRespDTO resp = new SkuAvailabilityRespDTO();
        resp.setSkuId(skuId);
        resp.setRequestedQuantity(quantity);

        ProductSkuDO sku = skuMapper.selectById(skuId);
        if (sku == null) {
            resp.setAvailable(false);
            resp.setReason("SKU_NOT_FOUND");
            return resp;
        }

        // Check status: SOLD_OUT/PAUSED/DEPRECATED → not available
        String status = sku.getStatus();
        if (SkuStatusEnum.SOLD_OUT.getCode().equals(status)
                || SkuStatusEnum.PAUSED.getCode().equals(status)
                || SkuStatusEnum.DEPRECATED.getCode().equals(status)) {
            resp.setAvailable(false);
            resp.setReason(status);
            return resp;
        }

        // Check per-order limit
        if (sku.getPerOrderLimit() != null && quantity != null && quantity > sku.getPerOrderLimit()) {
            resp.setAvailable(false);
            resp.setReason("EXCEEDS_PER_ORDER_LIMIT");
            resp.setMaxAllowedQuantity(sku.getPerOrderLimit());
            return resp;
        }

        // stockStrategy=TRACK_STOCK: only check status here, not stock quantity
        // (stock quantity check is done by StockApi cross-service call)
        resp.setAvailable(true);
        return resp;
    }

    @Override
    @GetMapping("/combo/{comboSkuId}/expand")
    public List<ComboItemRespDTO> expandCombo(Long comboSkuId) {
        requireTenantContext();

        // Find combo by combo_sku_id
        ProductComboDO combo = comboMapper.selectOne(
                ProductComboDO::getComboSkuId, comboSkuId);
        if (combo == null) {
            log.warn("Combo not found for comboSkuId={}", comboSkuId);
            return Collections.emptyList();
        }

        // Only expand ACTIVE combos
        if (!ComboStatusEnum.ACTIVE.getCode().equals(combo.getStatus())) {
            log.warn("Combo {} is not ACTIVE (status={}), returning empty list", combo.getId(), combo.getStatus());
            return Collections.emptyList();
        }

        List<ProductComboItemDO> items = comboItemMapper.selectList(
                ProductComboItemDO::getComboId, combo.getId());

        return items.stream()
                .map(item -> toComboItemRespDTO(item, combo))
                .collect(Collectors.toList());
    }

    @Override
    @GetMapping("/spu/{spuId}/addon-groups")
    public List<AddonGroupRespDTO> getAddonGroupsBySpu(Long spuId) {
        requireTenantContext();

        // Get addon group IDs via mapping table
        List<Long> addonGroupIds = spuAddonGroupService.getAddonGroupIdsBySpu(spuId);
        if (addonGroupIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<AddonGroupRespDTO> result = new ArrayList<>();
        for (Long groupId : addonGroupIds) {
            ProductAddonGroupDO group = addonGroupMapper.selectById(groupId);
            if (group == null) {
                continue; // skip deleted groups
            }

            AddonGroupRespDTO groupDTO = toAddonGroupRespDTO(group);
            // Only include ACTIVE options
            List<ProductAddonOptionDO> options = addonOptionMapper.selectList(
                    ProductAddonOptionDO::getAddonGroupId, groupId);
            List<AddonOptionRespDTO> optionDTOs = options.stream()
                    .filter(opt -> "ACTIVE".equals(opt.getStatus()))
                    .map(this::toAddonOptionRespDTO)
                    .collect(Collectors.toList());
            groupDTO.setOptions(optionDTOs);
            result.add(groupDTO);
        }

        return result;
    }

    // ===== Private helpers =====

    private void requireTenantContext() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.PRODUCT_API_NO_TENANT_CONTEXT);
        }
    }

    private SpuRespDTO toSpuRespDTO(ProductSpuDO spu) {
        SpuRespDTO dto = new SpuRespDTO();
        dto.setId(spu.getId());
        dto.setTenantId(spu.getTenantId());
        dto.setSpuCode(spu.getSpuCode());
        dto.setSpuName(spu.getSpuName());
        dto.setSpuShortName(spu.getSpuShortName());
        dto.setCategoryId(spu.getCategoryId());
        dto.setSpuType(spu.getSpuType());
        dto.setPrimaryImageUrl(spu.getPrimaryImageUrl());
        dto.setDescription(spu.getDescription());
        dto.setIsRecommended(spu.getIsRecommended());
        dto.setIsNewArrival(spu.getIsNewArrival());
        dto.setSortOrder(spu.getSortOrder());
        dto.setTotalSoldCount(spu.getTotalSoldCount());
        dto.setStatus(spu.getStatus());
        return dto;
    }

    private SkuRespDTO toSkuRespDTO(ProductSkuDO sku) {
        SkuRespDTO dto = new SkuRespDTO();
        dto.setId(sku.getId());
        dto.setTenantId(sku.getTenantId());
        dto.setSpuId(sku.getSpuId());
        dto.setSkuCode(sku.getSkuCode());
        dto.setSkuName(sku.getSkuName());
        dto.setSpecAttributes(sku.getSpecAttributes());
        dto.setListPrice(sku.getListPrice());
        dto.setSellingPrice(sku.getSellingPrice());
        dto.setCostPrice(sku.getCostPrice());
        dto.setMemberPrice(sku.getMemberPrice());
        dto.setDailyLimit(sku.getDailyLimit());
        dto.setPerOrderLimit(sku.getPerOrderLimit());
        dto.setMinOrderQuantity(sku.getMinOrderQuantity());
        dto.setStockStrategy(sku.getStockStrategy());
        dto.setStatus(sku.getStatus());
        dto.setStatusReason(sku.getStatusReason());
        dto.setPrimaryImageUrl(sku.getPrimaryImageUrl());
        dto.setTotalSoldCount(sku.getTotalSoldCount());
        return dto;
    }

    private ComboItemRespDTO toComboItemRespDTO(ProductComboItemDO item, ProductComboDO combo) {
        ComboItemRespDTO dto = new ComboItemRespDTO();
        dto.setComboId(combo.getId());
        dto.setComboSkuId(combo.getComboSkuId());
        dto.setItemSkuId(item.getItemSkuId());
        dto.setQuantity(item.getQuantity());
        dto.setIsOptional(item.getIsOptional());
        dto.setAlternativeGroup(item.getAlternativeGroup());

        // Populate item SKU name and selling price
        ProductSkuDO itemSku = skuMapper.selectById(item.getItemSkuId());
        if (itemSku != null) {
            dto.setItemSkuName(itemSku.getSkuName());
            dto.setItemSkuSellingPrice(itemSku.getSellingPrice());
        }

        return dto;
    }

    private AddonGroupRespDTO toAddonGroupRespDTO(ProductAddonGroupDO group) {
        AddonGroupRespDTO dto = new AddonGroupRespDTO();
        dto.setGroupId(group.getId());
        dto.setGroupCode(group.getGroupCode());
        dto.setGroupName(group.getGroupName());
        dto.setSelectMin(group.getSelectMin());
        dto.setSelectMax(group.getSelectMax());
        dto.setIsRequired(group.getIsRequired());
        dto.setSortOrder(group.getSortOrder());
        return dto;
    }

    private AddonOptionRespDTO toAddonOptionRespDTO(ProductAddonOptionDO option) {
        AddonOptionRespDTO dto = new AddonOptionRespDTO();
        dto.setOptionId(option.getId());
        dto.setOptionSkuId(option.getOptionSkuId());
        dto.setOptionName(option.getOptionName());
        dto.setExtraPrice(option.getExtraPrice());
        dto.setSortOrder(option.getSortOrder());
        dto.setStatus(option.getStatus());
        return dto;
    }
}
