package com.geihou.module.finance.product.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.finance.product.controller.admin.vo.*;
import com.geihou.module.finance.product.service.AddonGroupService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Addon group admin controller.
 *
 * <p>Base path: /admin-api/finance/product/addon-group
 * Provides CRUD for addon groups and addon options.
 * Addon option status uses ENUM_ADDON_OPTION_STATUS (ACTIVE/SOLD_OUT/DISABLED).
 */
@RestController
@RequestMapping("/admin-api/finance/product/addon-group")
public class AddonGroupController {

    private final AddonGroupService addonGroupService;

    public AddonGroupController(AddonGroupService addonGroupService) {
        this.addonGroupService = addonGroupService;
    }

    @PostMapping
    public CommonResult<Long> createAddonGroup(@RequestBody AddonGroupCreateReqVO reqVO) {
        Long id = addonGroupService.createAddonGroup(reqVO);
        return CommonResult.success(id);
    }

    @GetMapping
    public CommonResult<List<AddonGroupRespVO>> listAddonGroups() {
        List<AddonGroupRespVO> list = addonGroupService.listAddonGroups();
        return CommonResult.success(list);
    }

    @GetMapping("/{id}")
    public CommonResult<AddonGroupRespVO> getAddonGroup(@PathVariable Long id) {
        AddonGroupRespVO resp = addonGroupService.getAddonGroup(id);
        return CommonResult.success(resp);
    }

    @PutMapping("/{id}")
    public CommonResult<Boolean> updateAddonGroup(@PathVariable Long id,
                                                   @RequestBody AddonGroupUpdateReqVO reqVO) {
        addonGroupService.updateAddonGroup(id, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/{id}")
    public CommonResult<Boolean> deleteAddonGroup(@PathVariable Long id) {
        addonGroupService.deleteAddonGroup(id);
        return CommonResult.success(true);
    }

    @PostMapping("/{id}/option")
    public CommonResult<Long> createAddonOption(@PathVariable Long id,
                                                 @RequestBody AddonOptionCreateReqVO reqVO) {
        Long optionId = addonGroupService.createAddonOption(id, reqVO);
        return CommonResult.success(optionId);
    }

    @PutMapping("/{id}/option/{optionId}")
    public CommonResult<Boolean> updateAddonOption(@PathVariable Long id,
                                                    @PathVariable Long optionId,
                                                    @RequestBody AddonOptionUpdateReqVO reqVO) {
        addonGroupService.updateAddonOption(id, optionId, reqVO);
        return CommonResult.success(true);
    }

    @PutMapping("/{id}/option/{optionId}/status")
    public CommonResult<Boolean> changeAddonOptionStatus(@PathVariable Long id,
                                                          @PathVariable Long optionId,
                                                          @RequestBody AddonOptionStatusChangeReqVO reqVO) {
        addonGroupService.changeAddonOptionStatus(id, optionId, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/{id}/option/{optionId}")
    public CommonResult<Boolean> deleteAddonOption(@PathVariable Long id,
                                                    @PathVariable Long optionId) {
        addonGroupService.deleteAddonOption(id, optionId);
        return CommonResult.success(true);
    }
}
