package com.geihou.module.finance.product.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.finance.product.controller.admin.vo.*;
import com.geihou.module.finance.product.service.SpuService;
import org.springframework.web.bind.annotation.*;

/**
 * SPU admin controller.
 *
 * <p>Base path: /admin-api/finance/product/spu
 * Provides CRUD + status change endpoints for SPU management.
 * Tenant isolation is enforced at the service/mapper level.
 *
 * <p>Note: @OperateLog (Track A1 lesson 1) is not yet available in the geihou framework.
 * VO field names are designed to be compatible once the operate log framework is added.
 */
@RestController
@RequestMapping("/admin-api/finance/product/spu")
public class SpuController {

    private final SpuService spuService;

    public SpuController(SpuService spuService) {
        this.spuService = spuService;
    }

    @PostMapping
    public CommonResult<Long> createSpu(@RequestBody SpuCreateReqVO reqVO) {
        Long id = spuService.createSpu(reqVO);
        return CommonResult.success(id);
    }

    @GetMapping
    public CommonResult<PageResult<SpuRespVO>> pageSpu(SpuPageReqVO reqVO) {
        PageResult<SpuRespVO> page = spuService.pageSpu(reqVO);
        return CommonResult.success(page);
    }

    @GetMapping("/{id}")
    public CommonResult<SpuRespVO> getSpu(@PathVariable Long id) {
        SpuRespVO spu = spuService.getSpu(id);
        return CommonResult.success(spu);
    }

    @PutMapping("/{id}")
    public CommonResult<Boolean> updateSpu(@PathVariable Long id, @RequestBody SpuUpdateReqVO reqVO) {
        reqVO.setId(id);
        spuService.updateSpu(reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/{id}")
    public CommonResult<Boolean> deleteSpu(@PathVariable Long id) {
        spuService.deleteSpu(id);
        return CommonResult.success(true);
    }

    @PutMapping("/{id}/status")
    public CommonResult<Boolean> changeSpuStatus(@PathVariable Long id,
                                                  @RequestBody SpuStatusChangeReqVO reqVO,
                                                  @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        spuService.changeSpuStatus(id, reqVO, userId != null ? userId : 0L);
        return CommonResult.success(true);
    }
}
