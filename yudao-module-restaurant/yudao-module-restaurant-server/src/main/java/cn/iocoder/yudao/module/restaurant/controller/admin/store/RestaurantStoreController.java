package cn.iocoder.yudao.module.restaurant.controller.admin.store;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStoreCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStorePageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStoreRespVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStoreUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.convert.store.RestaurantStoreConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import cn.iocoder.yudao.module.restaurant.service.store.RestaurantStoreService;
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

@Tag(name = "管理后台 - 门店")
@RestController
@RequestMapping("/restaurant/store")
@Validated
public class RestaurantStoreController {

    @Resource
    private RestaurantStoreService storeService;

    @PostMapping("/create")
    @Operation(summary = "创建门店")
    @ApiAccessLog(operateType = CREATE)
    @PreAuthorize("@ss.hasPermission('restaurant:store:create')")
    public CommonResult<Long> createStore(@Valid @RequestBody RestaurantStoreCreateReqVO createReqVO) {
        return success(storeService.createStore(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新门店")
    @ApiAccessLog(operateType = UPDATE)
    @PreAuthorize("@ss.hasPermission('restaurant:store:update')")
    public CommonResult<Boolean> updateStore(@Valid @RequestBody RestaurantStoreUpdateReqVO updateReqVO) {
        storeService.updateStore(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除门店")
    @Parameter(name = "id", description = "编号", required = true)
    @ApiAccessLog(operateType = DELETE)
    @PreAuthorize("@ss.hasPermission('restaurant:store:delete')")
    public CommonResult<Boolean> deleteStore(@RequestParam("id") Long id) {
        storeService.deleteStore(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得门店")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:store:query')")
    public CommonResult<RestaurantStoreRespVO> getStore(@RequestParam("id") Long id) {
        RestaurantStoreDO store = storeService.getStore(id);
        return success(RestaurantStoreConvert.INSTANCE.convert(store));
    }

    @GetMapping("/list-all-simple")
    @Operation(summary = "获取门店精简信息列表", description = "主要用于前端的下拉选项")
    @ApiAccessLog(operateType = GET)
    public CommonResult<List<RestaurantStoreRespVO>> getSimpleStoreList() {
        List<RestaurantStoreDO> list = storeService.getStoreList();
        return success(RestaurantStoreConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/list")
    @Operation(summary = "获得门店列表")
    @Parameter(name = "ids", description = "编号列表", required = true, example = "1,2")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:store:query')")
    public CommonResult<List<RestaurantStoreRespVO>> getStoreList(@RequestParam("ids") Collection<Long> ids) {
        List<RestaurantStoreDO> list = storeService.getStoreList(ids);
        return success(RestaurantStoreConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/page")
    @Operation(summary = "获得门店分页")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:store:query')")
    public CommonResult<PageResult<RestaurantStoreRespVO>> getStorePage(@Valid RestaurantStorePageReqVO pageVO) {
        PageResult<RestaurantStoreDO> pageResult = storeService.getStorePage(pageVO);
        return success(RestaurantStoreConvert.INSTANCE.convertPage(pageResult));
    }

}
