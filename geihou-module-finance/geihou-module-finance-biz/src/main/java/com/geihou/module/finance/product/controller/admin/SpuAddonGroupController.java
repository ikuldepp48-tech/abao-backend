package com.geihou.module.finance.product.controller.admin;

import com.geihou.module.finance.product.controller.admin.vo.SpuAddonGroupRespVO;
import com.geihou.module.finance.product.service.SpuAddonGroupService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin controller for SPU-addon group mapping management.
 *
 * <p>Endpoints for assigning/listing/removing addon groups on an SPU.
 * Path prefix: /admin-api/finance/product/spu-addon-group
 */
@RestController
@RequestMapping("/admin-api/finance/product/spu-addon-group")
public class SpuAddonGroupController {

    private final SpuAddonGroupService spuAddonGroupService;

    public SpuAddonGroupController(SpuAddonGroupService spuAddonGroupService) {
        this.spuAddonGroupService = spuAddonGroupService;
    }

    /**
     * Assign an addon group to an SPU.
     */
    @PostMapping
    public Long assignAddonGroup(@RequestParam Long spuId,
                                  @RequestParam Long addonGroupId,
                                  @RequestParam(required = false) Integer sortOrder) {
        return spuAddonGroupService.assignAddonGroup(spuId, addonGroupId, sortOrder);
    }

    /**
     * List addon group mappings for an SPU.
     */
    @GetMapping("/{spuId}")
    public List<SpuAddonGroupRespVO> listBySpu(@PathVariable Long spuId) {
        return spuAddonGroupService.listBySpu(spuId);
    }

    /**
     * Remove an addon group mapping from an SPU (soft delete).
     */
    @DeleteMapping
    public void removeAddonGroup(@RequestParam Long spuId, @RequestParam Long addonGroupId) {
        spuAddonGroupService.removeAddonGroup(spuId, addonGroupId);
    }
}
