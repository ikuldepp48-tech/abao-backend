package cn.iocoder.yudao.module.restaurant.controller.admin.dish;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.*;
import cn.iocoder.yudao.module.restaurant.convert.dish.RestaurantDishSkuConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSkuDO;
import cn.iocoder.yudao.module.restaurant.service.dish.RestaurantDishSkuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 菜品SKU")
@RestController
@RequestMapping("/restaurant/dish-sku")
@Validated
public class RestaurantDishSkuController {

    @Resource
    private RestaurantDishSkuService skuService;

    @PostMapping("/create")
    @Operation(summary = "创建菜品SKU")
    @ApiAccessLog(operateType = CREATE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:create')")
    public CommonResult<Long> createSku(@Valid @RequestBody RestaurantDishSkuCreateReqVO createReqVO) {
        return success(skuService.createSku(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新菜品SKU")
    @ApiAccessLog(operateType = UPDATE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:update')")
    public CommonResult<Boolean> updateSku(@Valid @RequestBody RestaurantDishSkuUpdateReqVO updateReqVO) {
        skuService.updateSku(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除菜品SKU")
    @Parameter(name = "id", description = "编号", required = true)
    @ApiAccessLog(operateType = DELETE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:delete')")
    public CommonResult<Boolean> deleteSku(@RequestParam("id") Long id) {
        skuService.deleteSku(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得菜品SKU")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<RestaurantDishSkuRespVO> getSku(@RequestParam("id") Long id) {
        RestaurantDishSkuDO sku = skuService.getSku(id);
        return success(RestaurantDishSkuConvert.INSTANCE.convert(sku));
    }

    @GetMapping("/list-by-spu")
    @Operation(summary = "根据SPU获取SKU列表")
    @Parameter(name = "spuId", description = "SPU编号", required = true)
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<List<RestaurantDishSkuRespVO>> getSkuListBySpuId(@RequestParam("spuId") Long spuId) {
        List<RestaurantDishSkuDO> list = skuService.getSkuListBySpuId(spuId);
        return success(RestaurantDishSkuConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/page")
    @Operation(summary = "获得菜品SKU分页")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<PageResult<RestaurantDishSkuRespVO>> getSkuPage(@Valid RestaurantDishSkuPageReqVO pageVO) {
        PageResult<RestaurantDishSkuDO> pageResult = skuService.getSkuPage(pageVO);
        return success(RestaurantDishSkuConvert.INSTANCE.convertPage(pageResult));
    }

}
