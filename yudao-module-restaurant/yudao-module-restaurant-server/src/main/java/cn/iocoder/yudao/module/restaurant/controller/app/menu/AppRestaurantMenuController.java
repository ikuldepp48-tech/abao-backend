package cn.iocoder.yudao.module.restaurant.controller.app.menu;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.restaurant.controller.app.menu.vo.AppMenuRespVO;
import cn.iocoder.yudao.module.restaurant.service.menu.RestaurantMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.*;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;

@Tag(name = "用户端 - 菜单查询")
@RestController
@RequestMapping("/restaurant/menu")
@Validated
public class AppRestaurantMenuController {

    @Resource
    private RestaurantMenuService menuService;

    @ApiAccessLog(operateType = GET)
    @GetMapping("/list")
    @Operation(summary = "获取门店菜单（分类树+菜品+SKU+加料+套餐）")
    @Parameter(name = "storeId", description = "门店ID", required = true, example = "1")
    @PermitAll
    public CommonResult<AppMenuRespVO> getMenu(@RequestParam("storeId") Long storeId) {
        return success(menuService.getMenu(storeId));
    }

    @ApiAccessLog(operateType = DELETE)
    @DeleteMapping("/refresh-cache")
    @Operation(summary = "刷新菜单缓存")
    @Parameter(name = "storeId", description = "门店ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('restaurant:dish:update')")
    public CommonResult<Boolean> refreshCache(@RequestParam("storeId") Long storeId) {
        menuService.evictCache(storeId);
        return success(true);
    }

}
