package cn.iocoder.yudao.module.restaurant.controller.admin.brand;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandPageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandRespVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.convert.brand.RestaurantBrandConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.brand.RestaurantBrandDO;
import cn.iocoder.yudao.module.restaurant.service.brand.RestaurantBrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 餐饮品牌")
@RestController
@RequestMapping("/restaurant/brand")
@Validated
public class RestaurantBrandController {

    @Resource
    private RestaurantBrandService brandService;

    @PostMapping("/create")
    @Operation(summary = "创建餐饮品牌")
    @ApiAccessLog(operateType = CREATE)
    @PreAuthorize("@ss.hasPermission('restaurant:brand:create')")
    public CommonResult<Long> createBrand(@Valid @RequestBody RestaurantBrandCreateReqVO createReqVO) {
        return success(brandService.createBrand(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新餐饮品牌")
    @ApiAccessLog(operateType = UPDATE)
    @PreAuthorize("@ss.hasPermission('restaurant:brand:update')")
    public CommonResult<Boolean> updateBrand(@Valid @RequestBody RestaurantBrandUpdateReqVO updateReqVO) {
        brandService.updateBrand(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除餐饮品牌")
    @Parameter(name = "id", description = "编号", required = true)
    @ApiAccessLog(operateType = DELETE)
    @PreAuthorize("@ss.hasPermission('restaurant:brand:delete')")
    public CommonResult<Boolean> deleteBrand(@RequestParam("id") Long id) {
        brandService.deleteBrand(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得餐饮品牌")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:brand:query')")
    public CommonResult<RestaurantBrandRespVO> getBrand(@RequestParam("id") Long id) {
        RestaurantBrandDO brand = brandService.getBrand(id);
        return success(RestaurantBrandConvert.INSTANCE.convert(brand));
    }

    @GetMapping("/list-all-simple")
    @Operation(summary = "获取餐饮品牌精简信息列表", description = "主要用于前端的下拉选项")
    @ApiAccessLog(operateType = GET)
    public CommonResult<List<RestaurantBrandRespVO>> getSimpleBrandList() {
        List<RestaurantBrandDO> list = brandService.getBrandList();
        return success(RestaurantBrandConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/list")
    @Operation(summary = "获得餐饮品牌列表")
    @Parameter(name = "ids", description = "编号列表", required = true, example = "1,2")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:brand:query')")
    public CommonResult<List<RestaurantBrandRespVO>> getBrandList(@RequestParam("ids") Collection<Long> ids) {
        List<RestaurantBrandDO> list = brandService.getBrandList(ids);
        return success(RestaurantBrandConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/page")
    @Operation(summary = "获得餐饮品牌分页")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:brand:query')")
    public CommonResult<PageResult<RestaurantBrandRespVO>> getBrandPage(@Valid RestaurantBrandPageReqVO pageVO) {
        PageResult<RestaurantBrandDO> pageResult = brandService.getBrandPage(pageVO);
        return success(RestaurantBrandConvert.INSTANCE.convertPage(pageResult));
    }

}
