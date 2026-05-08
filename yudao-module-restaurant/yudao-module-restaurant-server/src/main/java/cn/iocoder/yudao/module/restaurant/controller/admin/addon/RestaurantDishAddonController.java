package cn.iocoder.yudao.module.restaurant.controller.admin.addon;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo.*;
import cn.iocoder.yudao.module.restaurant.convert.addon.RestaurantDishAddonConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishAddonDO;
import cn.iocoder.yudao.module.restaurant.service.addon.RestaurantDishAddonService;
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

@Tag(name = "管理后台 - 菜品加料")
@RestController
@RequestMapping("/restaurant/dish-addon")
@Validated
public class RestaurantDishAddonController {

    @Resource
    private RestaurantDishAddonService addonService;

    @PostMapping("/create")
    @Operation(summary = "创建加料")
    @ApiAccessLog(operateType = CREATE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:create')")
    public CommonResult<Long> createAddon(@Valid @RequestBody RestaurantDishAddonCreateReqVO createReqVO) {
        return success(addonService.createAddon(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新加料")
    @ApiAccessLog(operateType = UPDATE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:update')")
    public CommonResult<Boolean> updateAddon(@Valid @RequestBody RestaurantDishAddonUpdateReqVO updateReqVO) {
        addonService.updateAddon(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除加料")
    @Parameter(name = "id", description = "编号", required = true)
    @ApiAccessLog(operateType = DELETE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:delete')")
    public CommonResult<Boolean> deleteAddon(@RequestParam("id") Long id) {
        addonService.deleteAddon(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得加料")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<RestaurantDishAddonRespVO> getAddon(@RequestParam("id") Long id) {
        RestaurantDishAddonDO addon = addonService.getAddon(id);
        return success(RestaurantDishAddonConvert.INSTANCE.convert(addon));
    }

    @GetMapping("/list-by-group")
    @Operation(summary = "根据加料组获取加料列表")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<List<RestaurantDishAddonRespVO>> getAddonListByGroupName(
            @RequestParam("brandId") Long brandId,
            @RequestParam("groupName") String groupName) {
        List<RestaurantDishAddonDO> list = addonService.getAddonListByGroupName(brandId, groupName);
        return success(RestaurantDishAddonConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/group-names")
    @Operation(summary = "获取加料组名称列表")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<List<String>> getDistinctGroupNames(@RequestParam("brandId") Long brandId) {
        return success(addonService.getDistinctGroupNames(brandId));
    }

    @GetMapping("/list-by-brand")
    @Operation(summary = "根据品牌获取全部加料列表")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<List<RestaurantDishAddonRespVO>> getAddonListByBrand(@RequestParam("brandId") Long brandId) {
        List<RestaurantDishAddonDO> list = addonService.getAddonListByBrand(brandId);
        return success(RestaurantDishAddonConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/page")
    @Operation(summary = "获得加料分页")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<PageResult<RestaurantDishAddonRespVO>> getAddonPage(@Valid RestaurantDishAddonPageReqVO pageVO) {
        PageResult<RestaurantDishAddonDO> pageResult = addonService.getAddonPage(pageVO);
        return success(RestaurantDishAddonConvert.INSTANCE.convertPage(pageResult));
    }

}
