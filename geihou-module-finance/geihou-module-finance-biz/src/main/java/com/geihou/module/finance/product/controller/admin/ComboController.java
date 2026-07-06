package com.geihou.module.finance.product.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.finance.product.controller.admin.vo.*;
import com.geihou.module.finance.product.service.ComboService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Combo admin controller.
 *
 * <p>Base path: /admin-api/finance/product/combo
 * Provides CRUD for combos and combo items, plus status management.
 * Combo status uses ENUM_COMBO_STATUS (ACTIVE/PAUSED/DEPRECATED).
 */
@RestController
@RequestMapping("/admin-api/finance/product/combo")
public class ComboController {

    private final ComboService comboService;

    public ComboController(ComboService comboService) {
        this.comboService = comboService;
    }

    @PostMapping
    public CommonResult<Long> createCombo(@RequestBody ComboCreateReqVO reqVO) {
        Long id = comboService.createCombo(reqVO);
        return CommonResult.success(id);
    }

    @GetMapping
    public CommonResult<List<ComboRespVO>> listCombos() {
        List<ComboRespVO> list = comboService.listCombos();
        return CommonResult.success(list);
    }

    @GetMapping("/{id}")
    public CommonResult<ComboRespVO> getCombo(@PathVariable Long id) {
        ComboRespVO resp = comboService.getCombo(id);
        return CommonResult.success(resp);
    }

    @PutMapping("/{id}")
    public CommonResult<Boolean> updateCombo(@PathVariable Long id,
                                              @RequestBody ComboUpdateReqVO reqVO) {
        comboService.updateCombo(id, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/{id}")
    public CommonResult<Boolean> deleteCombo(@PathVariable Long id) {
        comboService.deleteCombo(id);
        return CommonResult.success(true);
    }

    @PutMapping("/{id}/status")
    public CommonResult<Boolean> changeComboStatus(@PathVariable Long id,
                                                    @RequestBody ComboStatusChangeReqVO reqVO,
                                                    @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        comboService.changeComboStatus(id, reqVO, userId != null ? userId : 0L);
        return CommonResult.success(true);
    }

    @PostMapping("/{id}/item")
    public CommonResult<Long> addComboItem(@PathVariable Long id,
                                            @RequestBody ComboItemCreateReqVO reqVO) {
        Long itemId = comboService.addComboItem(id, reqVO);
        return CommonResult.success(itemId);
    }

    @PutMapping("/{id}/item/{itemId}")
    public CommonResult<Boolean> updateComboItem(@PathVariable Long id,
                                                  @PathVariable Long itemId,
                                                  @RequestBody ComboItemUpdateReqVO reqVO) {
        comboService.updateComboItem(id, itemId, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/{id}/item/{itemId}")
    public CommonResult<Boolean> deleteComboItem(@PathVariable Long id,
                                                  @PathVariable Long itemId) {
        comboService.deleteComboItem(id, itemId);
        return CommonResult.success(true);
    }
}
