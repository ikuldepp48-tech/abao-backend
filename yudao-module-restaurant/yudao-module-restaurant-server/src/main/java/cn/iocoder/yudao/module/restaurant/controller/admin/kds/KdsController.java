package cn.iocoder.yudao.module.restaurant.controller.admin.kds;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderItemDO;
import cn.iocoder.yudao.module.restaurant.service.kds.KdsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - KDS 厨房显示")
@RestController
@RequestMapping("/restaurant/kds")
@Slf4j
public class KdsController {

    @Resource
    private KdsService kdsService;

    @GetMapping("/order-list")
    @Operation(summary = "获取档口订单列表")
    @PermitAll
    public CommonResult<Map<Long, List<RestaurantOrderItemDO>>> getOrderList(@RequestParam("stationId") Long stationId) {
        return success(kdsService.getStationOrderItems(stationId));
    }

    @PutMapping("/start")
    @Operation(summary = "开始制作")
    @PermitAll
    public CommonResult<Boolean> startItem(@RequestParam("itemId") Long itemId) {
        kdsService.startItem(itemId);
        return success(true);
    }

    @PutMapping("/finish")
    @Operation(summary = "完成出餐")
    @PermitAll
    public CommonResult<Boolean> finishItem(@RequestParam("itemId") Long itemId) {
        kdsService.finishItem(itemId);
        return success(true);
    }

}
