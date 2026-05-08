package cn.iocoder.yudao.module.restaurant.controller.admin.combo;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.combo.vo.*;
import cn.iocoder.yudao.module.restaurant.convert.combo.RestaurantComboConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.combo.RestaurantComboDO;
import cn.iocoder.yudao.module.restaurant.service.combo.RestaurantComboService;
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

@Tag(name = "管理后台 - 套餐管理")
@RestController
@RequestMapping("/restaurant/combo")
@Validated
public class RestaurantComboController {

    @Resource
    private RestaurantComboService comboService;

    @PostMapping("/create")
    @Operation(summary = "创建套餐")
    @ApiAccessLog(operateType = CREATE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:create')")
    public CommonResult<Long> createCombo(@Valid @RequestBody RestaurantComboCreateReqVO createReqVO) {
        return success(comboService.createCombo(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新套餐")
    @ApiAccessLog(operateType = UPDATE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:update')")
    public CommonResult<Boolean> updateCombo(@Valid @RequestBody RestaurantComboUpdateReqVO updateReqVO) {
        comboService.updateCombo(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除套餐")
    @Parameter(name = "id", description = "编号", required = true)
    @ApiAccessLog(operateType = DELETE)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:delete')")
    public CommonResult<Boolean> deleteCombo(@RequestParam("id") Long id) {
        comboService.deleteCombo(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得套餐")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<RestaurantComboRespVO> getCombo(@RequestParam("id") Long id) {
        RestaurantComboDO combo = comboService.getCombo(id);
        RestaurantComboRespVO respVO = RestaurantComboConvert.INSTANCE.convert(combo);
        List<RestaurantComboItemBaseVO> items = comboService.getComboItems(id);
        respVO.setItems(items);
        return success(respVO);
    }

    @GetMapping("/items")
    @Operation(summary = "获得套餐明细")
    @Parameter(name = "comboId", description = "套餐编号", required = true)
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<List<RestaurantComboItemBaseVO>> getComboItems(@RequestParam("comboId") Long comboId) {
        return success(comboService.getComboItems(comboId));
    }

    @GetMapping("/list-by-brand")
    @Operation(summary = "根据品牌获取套餐列表")
    @Parameter(name = "brandId", description = "品牌ID", required = true)
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<List<RestaurantComboRespVO>> getComboListByBrandId(@RequestParam("brandId") Long brandId) {
        List<RestaurantComboDO> list = comboService.getComboListByBrandId(brandId);
        return success(RestaurantComboConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/page")
    @Operation(summary = "获得套餐分页")
    @ApiAccessLog(operateType = GET)
    @PreAuthorize("@ss.hasPermission('restaurant:dish:query')")
    public CommonResult<PageResult<RestaurantComboRespVO>> getComboPage(@Valid RestaurantComboPageReqVO pageVO) {
        PageResult<RestaurantComboDO> pageResult = comboService.getComboPage(pageVO);
        return success(RestaurantComboConvert.INSTANCE.convertPage(pageResult));
    }

}
