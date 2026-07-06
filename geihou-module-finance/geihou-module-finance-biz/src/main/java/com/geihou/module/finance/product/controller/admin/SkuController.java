package com.geihou.module.finance.product.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.finance.product.controller.admin.vo.*;
import com.geihou.module.finance.product.service.PriceService;
import com.geihou.module.finance.product.service.SkuService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SKU admin controller.
 *
 * <p>Base path: /admin-api/finance/product/sku
 * Provides CRUD, price change, and price history endpoints for SKU management.
 * Price changes force history logging (Invariance 11).
 *
 * <p>Note: @OperateLog (Track A1 lesson 1) is not yet available in the geihou framework.
 * VO field names (newSellingPrice, reason, changeType) are designed to be compatible
 * once the operate log framework is added.
 */
@RestController
@RequestMapping("/admin-api/finance/product/sku")
public class SkuController {

    private final SkuService skuService;
    private final PriceService priceService;

    public SkuController(SkuService skuService, PriceService priceService) {
        this.skuService = skuService;
        this.priceService = priceService;
    }

    @PostMapping
    public CommonResult<Long> createSku(@RequestBody SkuCreateReqVO reqVO) {
        Long id = skuService.createSku(reqVO);
        return CommonResult.success(id);
    }

    @GetMapping("/{id}")
    public CommonResult<SkuRespVO> getSku(@PathVariable Long id) {
        SkuRespVO sku = skuService.getSku(id);
        return CommonResult.success(sku);
    }

    @PutMapping("/{id}")
    public CommonResult<Boolean> updateSku(@PathVariable Long id, @RequestBody SkuUpdateReqVO reqVO) {
        reqVO.setId(id);
        skuService.updateSku(reqVO);
        return CommonResult.success(true);
    }

    @PutMapping("/{id}/price")
    public CommonResult<Boolean> changePrice(@PathVariable Long id,
                                              @RequestBody SkuPriceChangeReqVO reqVO,
                                              @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        priceService.changePrice(id, reqVO, userId != null ? userId : 0L);
        return CommonResult.success(true);
    }

    @GetMapping("/{id}/price-history")
    public CommonResult<List<PriceHistoryRespVO>> getPriceHistory(@PathVariable Long id) {
        List<PriceHistoryRespVO> history = priceService.getPriceHistory(id);
        return CommonResult.success(history);
    }
}
