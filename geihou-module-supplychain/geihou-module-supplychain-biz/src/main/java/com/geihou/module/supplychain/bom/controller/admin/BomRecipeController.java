package com.geihou.module.supplychain.bom.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.supplychain.api.bom.dto.BomRecipeRespDTO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeItemDO;
import com.geihou.module.supplychain.bom.explode.BomApiImpl;
import com.geihou.module.supplychain.bom.service.BomRecipeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Admin controller for BOM recipe management.
 *
 * <p>Provides recipe lifecycle: create draft, update items, activate, query.
 * All operations are tenant-isolated via explicit tenantId parameter.
 *
 * <p>Source: TASK-G2-02A.
 */
@RestController
@RequestMapping("/admin/bom/recipe")
public class BomRecipeController {

    @Autowired
    private BomRecipeService bomRecipeService;
    @Autowired
    private BomApiImpl bomApi;

    /**
     * Create a draft recipe for a product.
     */
    @PostMapping
    public CommonResult<Long> createDraft(@RequestBody RecipeCreateReq req) {
        Objects.requireNonNull(req.getTenantId(), "tenantId must not be null");
        Objects.requireNonNull(req.getProductId(), "productId must not be null");

        BomRecipeDO recipe = new BomRecipeDO();
        recipe.setTenantId(req.getTenantId());
        recipe.setProductId(req.getProductId());
        recipe.setRemark(req.getRemark());

        Long id = bomRecipeService.createDraftRecipe(recipe);
        return CommonResult.success(id);
    }

    /**
     * Get recipe by id (tenant-isolated).
     */
    @GetMapping("/{id}")
    public CommonResult<BomRecipeDO> getById(@PathVariable Long id,
                                              @RequestParam Long tenantId) {
        BomRecipeDO recipe = bomRecipeService.getById(id, tenantId);
        return CommonResult.success(recipe);
    }

    /**
     * Get items for a recipe (tenant-isolated).
     */
    @GetMapping("/{id}/items")
    public CommonResult<List<BomRecipeItemDO>> getItems(@PathVariable Long id,
                                                          @RequestParam Long tenantId) {
        List<BomRecipeItemDO> items = bomRecipeService.getItems(id, tenantId);
        return CommonResult.success(items);
    }

    /**
     * Update items for a DRAFT recipe (batch replace).
     */
    @PutMapping("/{id}/items")
    public CommonResult<Boolean> updateItems(@PathVariable Long id,
                                              @RequestParam Long tenantId,
                                              @RequestBody RecipeItemsUpdateReq req) {
        List<BomRecipeItemDO> items = new ArrayList<>();
        if (req.getItems() != null) {
            for (RecipeItemReq itemReq : req.getItems()) {
                BomRecipeItemDO item = new BomRecipeItemDO();
                item.setComponentProductId(itemReq.getComponentProductId());
                item.setQuantity(itemReq.getQuantity());
                item.setWasteRate(itemReq.getWasteRate());
                items.add(item);
            }
        }

        bomRecipeService.updateDraftItems(tenantId, id, items);
        return CommonResult.success(true);
    }

    /**
     * Activate a DRAFT recipe.
     */
    @PutMapping("/{id}/activate")
    public CommonResult<Boolean> activate(@PathVariable Long id,
                                           @RequestParam Long tenantId) {
        boolean activated = bomRecipeService.activateRecipe(tenantId, id);
        return CommonResult.success(activated);
    }

    /**
     * Get the active recipe for a product.
     */
    @GetMapping("/active")
    public CommonResult<BomRecipeDO> getActive(@RequestParam Long productId,
                                                 @RequestParam Long tenantId) {
        BomRecipeDO recipe = bomRecipeService.getActiveRecipe(productId, tenantId);
        return CommonResult.success(recipe);
    }

    /**
     * Get the active recipe (header + items) for a product.
     * Returns an explicit empty-state DTO (id=null, items=[]) if no active recipe.
     *
     * <p>Source: TASK-G2-02G.
     */
    @GetMapping("/active/detail")
    public CommonResult<BomRecipeRespDTO> getActiveDetail(@RequestParam Long tenantId,
                                                            @RequestParam Long productId) {
        BomRecipeRespDTO resp = bomApi.getActiveRecipe(tenantId, productId);
        return CommonResult.success(resp);
    }

    // --- Request VOs ---

    public static class RecipeCreateReq {
        private Long tenantId;
        private Long productId;
        private String remark;

        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public static class RecipeItemsUpdateReq {
        private List<RecipeItemReq> items;

        public List<RecipeItemReq> getItems() { return items; }
        public void setItems(List<RecipeItemReq> items) { this.items = items; }
    }

    public static class RecipeItemReq {
        private Long componentProductId;
        private BigDecimal quantity;
        private BigDecimal wasteRate;

        public Long getComponentProductId() { return componentProductId; }
        public void setComponentProductId(Long componentProductId) { this.componentProductId = componentProductId; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public BigDecimal getWasteRate() { return wasteRate; }
        public void setWasteRate(BigDecimal wasteRate) { this.wasteRate = wasteRate; }
    }
}
