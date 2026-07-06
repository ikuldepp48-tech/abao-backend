package com.geihou.module.supplychain.bom.explode;

import com.geihou.module.supplychain.api.bom.BomApi;
import com.geihou.module.supplychain.api.bom.dto.BomRecipeRespDTO;
import com.geihou.module.supplychain.api.bom.explode.dto.BomExplosionRespDTO;
import com.geihou.module.supplychain.api.bom.preview.dto.BomCostPreviewRespDTO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeItemDO;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.dal.mapper.ProductMasterMapper;
import com.geihou.module.supplychain.bom.preview.BomCostPreviewService;
import com.geihou.module.supplychain.bom.service.BomRecipeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link BomApi}.
 *
 * <p>Delegates to {@link BomExplosionService}, {@link BomCostPreviewService},
 * and {@link BomRecipeService}. Read-only — no write methods.
 *
 * <p>Source: TASK-G2-02B. getActiveRecipe added in TASK-G2-02G.
 */
@Service
public class BomApiImpl implements BomApi {

    private static final Logger log = LoggerFactory.getLogger(BomApiImpl.class);

    @Autowired
    private BomExplosionService bomExplosionService;
    @Autowired
    private BomCostPreviewService bomCostPreviewService;
    @Autowired
    private BomRecipeService bomRecipeService;
    @Autowired
    private ProductMasterMapper productMasterMapper;

    @Override
    public List<BomExplosionRespDTO> explode(Long productId, Long recipeId,
                                              BigDecimal requestedQuantity, Long tenantId) {
        return bomExplosionService.explode(productId, recipeId, requestedQuantity, tenantId);
    }

    @Override
    public List<BomCostPreviewRespDTO> previewCost(Long productId, Long recipeId,
                                                    BigDecimal requestedQuantity, Long tenantId) {
        return bomCostPreviewService.previewCost(productId, recipeId, requestedQuantity, tenantId);
    }

    @Override
    public BomRecipeRespDTO getActiveRecipe(Long tenantId, Long productId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(productId, "productId must not be null");

        BomRecipeDO recipe = bomRecipeService.getActiveRecipe(productId, tenantId);

        // No active recipe → return explicit empty-state DTO
        if (recipe == null) {
            BomRecipeRespDTO empty = new BomRecipeRespDTO();
            empty.setId(null);
            empty.setTenantId(tenantId);
            empty.setProductId(productId);
            empty.setItems(new ArrayList<>());
            return empty;
        }

        // Load items
        List<BomRecipeItemDO> itemDOs = bomRecipeService.getItems(recipe.getId(), tenantId);

        // Convert header
        BomRecipeRespDTO resp = new BomRecipeRespDTO();
        resp.setId(recipe.getId());
        resp.setTenantId(recipe.getTenantId());
        resp.setProductId(recipe.getProductId());
        resp.setVersionNo(recipe.getVersionNo());
        resp.setStatus(recipe.getStatus());
        resp.setRemark(recipe.getRemark());
        resp.setOutputQuantity(recipe.getOutputQuantity());
        resp.setOutputUnit(recipe.getOutputUnit());
        resp.setCreator(recipe.getCreator());
        resp.setCreateTime(recipe.getCreateTime());
        resp.setUpdater(recipe.getUpdater());
        resp.setUpdateTime(recipe.getUpdateTime());

        // Convert items (look up product master for code/name)
        List<BomRecipeRespDTO.Item> items = new ArrayList<>();
        if (itemDOs != null) {
            for (BomRecipeItemDO itemDO : itemDOs) {
                BomRecipeRespDTO.Item item = new BomRecipeRespDTO.Item();
                item.setId(itemDO.getId());
                item.setComponentProductId(itemDO.getComponentProductId());
                item.setQuantity(itemDO.getQuantity());
                item.setWasteRate(itemDO.getWasteRate());
                item.setComponentType(itemDO.getComponentType());
                item.setUnit(itemDO.getUnit());

                // Enrich with product master code/name
                ProductMasterDO component = productMasterMapper.selectByIdAndTenant(
                        itemDO.getComponentProductId(), tenantId);
                if (component != null) {
                    item.setComponentProductCode(component.getProductCode());
                    item.setComponentProductName(component.getProductName());
                }

                items.add(item);
            }
        }
        resp.setItems(items);

        return resp;
    }

    @Override
    public BomRecipeRespDTO getActiveRecipeBySkuCode(Long tenantId, String skuCode) {
        // null/blank skuCode → empty-state DTO, no exception
        if (skuCode == null || skuCode.trim().isEmpty()) {
            return emptyStateDto();
        }

        List<ProductMasterDO> products = productMasterMapper.selectActiveFinishedByTenantSkuCode(
                tenantId, skuCode);

        // 0 matches → empty-state DTO
        if (products == null || products.isEmpty()) {
            return emptyStateDto();
        }

        // ≥2 matches → empty-state DTO + WARN log, do NOT pick the first
        if (products.size() >= 2) {
            log.warn("Multiple active FINISHED products found for tenantId={}, skuCode={}, count={}. " +
                     "Returning empty-state DTO without picking the first.",
                     tenantId, skuCode, products.size());
            return emptyStateDto();
        }

        // exactly 1 match → delegate to existing getActiveRecipe
        ProductMasterDO product = products.get(0);
        return getActiveRecipe(tenantId, product.getId());
    }

    /**
     * Build an empty-state {@link BomRecipeRespDTO}: id=null, productId=null, items=emptyList.
     * Non-null, non-throwing — suitable for finance guard read paths.
     */
    private BomRecipeRespDTO emptyStateDto() {
        BomRecipeRespDTO empty = new BomRecipeRespDTO();
        empty.setId(null);
        empty.setProductId(null);
        empty.setItems(Collections.emptyList());
        return empty;
    }
}
