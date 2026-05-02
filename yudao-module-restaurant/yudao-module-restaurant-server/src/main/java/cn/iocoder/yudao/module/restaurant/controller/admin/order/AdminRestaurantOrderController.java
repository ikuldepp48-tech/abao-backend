package cn.iocoder.yudao.module.restaurant.controller.admin.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.order.vo.RestaurantOrderPageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.app.order.vo.AppOrderRespVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderDO;
import cn.iocoder.yudao.module.restaurant.service.order.RestaurantOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 订单管理")
@RestController
@RequestMapping("/restaurant/order")
@Validated
public class AdminRestaurantOrderController {

    @Resource
    private RestaurantOrderService orderService;

    @GetMapping("/admin/page")
    @Operation(summary = "订单分页")
    @PreAuthorize("@ss.hasPermission('restaurant:order:query')")
    public CommonResult<PageResult<RestaurantOrderDO>> getOrderPage(@Valid RestaurantOrderPageReqVO pageVO) {
        return success(orderService.getOrderPage(pageVO));
    }

    @GetMapping("/admin/get")
    @Operation(summary = "订单详情")
    @Parameter(name = "id", description = "订单ID", required = true)
    @PreAuthorize("@ss.hasPermission('restaurant:order:query')")
    public CommonResult<AppOrderRespVO> getOrderDetail(@RequestParam("id") Long id) {
        return success(orderService.getOrderDetail(id));
    }

}
