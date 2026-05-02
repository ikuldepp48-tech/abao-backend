package cn.iocoder.yudao.module.restaurant.controller.admin.dish;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSpuCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSpuPageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSpuRespVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSpuUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSkuRespVO;
import cn.iocoder.yudao.module.restaurant.convert.dish.RestaurantDishSkuConvert;
import cn.iocoder.yudao.module.restaurant.convert.dish.RestaurantDishSpuConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSkuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO;
import cn.iocoder.yudao.module.restaurant.service.dish.RestaurantDishSkuService;
import cn.iocoder.yudao.module.restaurant.service.dish.RestaurantDishSpuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 菜品管理")
@RestController
@RequestMapping("/restaurant/dish-spu")
@Validated
public class RestaurantDishSpuController {

    @Resource
    private RestaurantDishSpuService dishSpuService;

    @Resource
    private RestaurantDishSkuService dishSkuService;

    @PostMapping("/create")
    @Operation(summary = "创建菜品")
    @PreAuthorize("@ss.hasPermission('restaurant:dish:create')")
    public CommonResult<Long> createDishSpu(@Valid @RequestBody RestaurantDishSpuCreateReqVO createReqVO) {
        return success(dishSpuService.createDishSpu(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新菜品")
    @PreAuthorize("@ss.hasPermission('restaurant:dish:update')")
    public CommonResult<Boolean> updateDishSpu(@Valid @RequestBody RestaurantDishSpuUpdateReqVO updateReqVO) {
        dishSpuService.updateDishSpu(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除菜品")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:delete')")
    public CommonResult<Boolean> deleteDishSpu(@RequestParam("id") Long id) {
        dishSpuService.deleteDishSpu(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得菜品")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<RestaurantDishSpuRespVO> getDishSpu(@RequestParam("id") Long id) {
        RestaurantDishSpuDO dish = dishSpuService.getDishSpu(id);
        RestaurantDishSpuRespVO respVO = RestaurantDishSpuConvert.INSTANCE.convert(dish);
        // 填充SKU列表
        List<RestaurantDishSkuDO> skus = dishSkuService.getSkuListBySpuId(id);
        respVO.setSkus(new ArrayList<>(RestaurantDishSkuConvert.INSTANCE.convertList(skus)));
        return success(respVO);
    }

    @GetMapping("/list-all-simple")
    @Operation(summary = "获取菜品精简信息列表", description = "主要用于前端的下拉选项")
    public CommonResult<List<RestaurantDishSpuRespVO>> getSimpleDishSpuList() {
        List<RestaurantDishSpuDO> list = dishSpuService.getDishSpuList();
        return success(RestaurantDishSpuConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/list")
    @Operation(summary = "获得菜品列表")
    @Parameter(name = "ids", description = "编号列表", required = true, example = "1,2")
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<List<RestaurantDishSpuRespVO>> getDishSpuList(@RequestParam("ids") Collection<Long> ids) {
        List<RestaurantDishSpuDO> list = dishSpuService.getDishSpuList(ids);
        return success(RestaurantDishSpuConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/page")
    @Operation(summary = "获得菜品分页")
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<PageResult<RestaurantDishSpuRespVO>> getDishSpuPage(@Valid RestaurantDishSpuPageReqVO pageVO) {
        PageResult<RestaurantDishSpuDO> pageResult = dishSpuService.getDishSpuPage(pageVO);
        return success(RestaurantDishSpuConvert.INSTANCE.convertPage(pageResult));
    }

}
