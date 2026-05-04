package cn.iocoder.yudao.module.restaurant.controller.admin.kitchen;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.kitchen.vo.RestaurantKitchenStationSaveReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.kitchen.RestaurantKitchenStationDO;
import cn.iocoder.yudao.module.restaurant.service.kitchen.RestaurantKitchenStationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 厨房档口")
@RestController
@RequestMapping("/restaurant/kitchen-station")
@Validated
public class RestaurantKitchenStationController {

    @Resource
    private RestaurantKitchenStationService stationService;

    @PostMapping("/create")
    @Operation(summary = "创建档口")
    @PreAuthorize("@ss.hasPermission('restaurant:kitchen-station:create')")
    public CommonResult<Long> createStation(@Valid @RequestBody RestaurantKitchenStationSaveReqVO reqVO) {
        return success(stationService.createStation(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新档口")
    @PreAuthorize("@ss.hasPermission('restaurant:kitchen-station:update')")
    public CommonResult<Boolean> updateStation(@Valid @RequestBody RestaurantKitchenStationSaveReqVO reqVO) {
        stationService.updateStation(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除档口")
    @PreAuthorize("@ss.hasPermission('restaurant:kitchen-station:delete')")
    public CommonResult<Boolean> deleteStation(@RequestParam("id") Long id) {
        stationService.deleteStation(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取档口详情")
    @PreAuthorize("@ss.hasPermission('restaurant:kitchen-station:query')")
    public CommonResult<RestaurantKitchenStationDO> getStation(@RequestParam("id") Long id) {
        return success(stationService.getStation(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获取档口分页")
    @PreAuthorize("@ss.hasPermission('restaurant:kitchen-station:query')")
    public CommonResult<PageResult<RestaurantKitchenStationDO>> getStationPage(@RequestParam(defaultValue = "1") Integer pageNo,
                                                                               @RequestParam(defaultValue = "10") Integer pageSize) {
        return success(stationService.getStationPage(pageNo, pageSize));
    }

}
