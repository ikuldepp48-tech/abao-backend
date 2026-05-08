package cn.iocoder.yudao.module.restaurant.controller.admin.store;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.*;
import cn.iocoder.yudao.module.restaurant.convert.store.RestaurantStoreDishConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDishDO;
import cn.iocoder.yudao.module.restaurant.service.store.RestaurantStoreDishService;
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

@Tag(name = "管理后台 - 门店菜品配置")
@RestController
@RequestMapping("/restaurant/store-dish")
@Validated
public class RestaurantStoreDishController {

    @Resource
    private RestaurantStoreDishService storeDishService;

    @PostMapping("/create")
    @Operation(summary = "创建门店菜品配置")
    @ApiAccessLog(operateType = CREATE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:create')")
    public CommonResult<Long> createStoreDish(@Valid @RequestBody RestaurantStoreDishCreateReqVO createReqVO) {
        return success(storeDishService.createStoreDish(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新门店菜品配置")
    @ApiAccessLog(operateType = UPDATE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:update')")
    public CommonResult<Boolean> updateStoreDish(@Valid @RequestBody RestaurantStoreDishUpdateReqVO updateReqVO) {
        storeDishService.updateStoreDish(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除门店菜品配置")
    @Parameter(name = "id", description = "编号", required = true)
    @ApiAccessLog(operateType = DELETE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:delete')")
    public CommonResult<Boolean> deleteStoreDish(@RequestParam("id") Long id) {
        storeDishService.deleteStoreDish(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得门店菜品配置")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<RestaurantStoreDishRespVO> getStoreDish(@RequestParam("id") Long id) {
        RestaurantStoreDishDO storeDish = storeDishService.getStoreDish(id);
        return success(RestaurantStoreDishConvert.INSTANCE.convert(storeDish));
    }

    @GetMapping("/list-by-store")
    @Operation(summary = "根据门店获取菜品配置列表")
    @Parameter(name = "storeId", description = "门店ID", required = true)
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<List<RestaurantStoreDishRespVO>> getStoreDishListByStoreId(@RequestParam("storeId") Long storeId) {
        List<RestaurantStoreDishDO> list = storeDishService.getStoreDishListByStoreId(storeId);
        return success(RestaurantStoreDishConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/page")
    @Operation(summary = "获得门店菜品配置分页")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<PageResult<RestaurantStoreDishRespVO>> getStoreDishPage(@Valid RestaurantStoreDishPageReqVO pageVO) {
        PageResult<RestaurantStoreDishDO> pageResult = storeDishService.getStoreDishPage(pageVO);
        return success(RestaurantStoreDishConvert.INSTANCE.convertPage(pageResult));
    }

    @PutMapping("/batch-sold-out")
    @Operation(summary = "一键沽清")
    @ApiAccessLog(operateType = UPDATE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:update')")
    public CommonResult<Integer> batchSoldOut(@RequestParam("ids") List<Long> ids) {
        return success(storeDishService.batchSoldOut(ids));
    }

    @PutMapping("/batch-restore")
    @Operation(summary = "批量恢复供应")
    @ApiAccessLog(operateType = UPDATE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:update')")
    public CommonResult<Integer> batchRestore(@RequestParam("ids") List<Long> ids) {
        return success(storeDishService.batchRestore(ids));
    }

    @PutMapping("/batch-update-status")
    @Operation(summary = "批量上下架")
    @ApiAccessLog(operateType = UPDATE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:update')")
    public CommonResult<Integer> batchUpdateStatus(@RequestParam("ids") List<Long> ids,
                                                    @RequestParam("status") Integer status) {
        return success(storeDishService.batchUpdateStatus(ids, status));
    }

    @PutMapping("/override-price")
    @Operation(summary = "覆盖门店菜品价格")
    @ApiAccessLog(operateType = UPDATE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:update')")
    public CommonResult<Integer> overridePrice(@RequestParam("id") Long id,
                                                @RequestParam("price") java.math.BigDecimal price) {
        return success(storeDishService.overridePrice(id, price));
    }

    @PutMapping("/set-daily-limit")
    @Operation(summary = "设置每日限量")
    @ApiAccessLog(operateType = UPDATE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:update')")
    public CommonResult<Integer> setDailyLimit(@RequestParam("id") Long id,
                                                @RequestParam("dailyLimit") Integer dailyLimit) {
        return success(storeDishService.setDailyLimit(id, dailyLimit));
    }

}
