package cn.iocoder.yudao.module.restaurant.controller.app.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.restaurant.controller.app.order.vo.AppOrderCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.app.order.vo.AppOrderRespVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderDO;
import cn.iocoder.yudao.module.restaurant.service.order.RestaurantOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "顾客端 - 订单")
@RestController
@RequestMapping("/restaurant/order")
@Validated
public class AppOrderController {

    @Resource
    private RestaurantOrderService orderService;

    @Resource
    private HttpServletRequest request;

    @PostMapping("/create")
    @Operation(summary = "创建订单")
    public CommonResult<AppOrderRespVO> createOrder(@Valid @RequestBody AppOrderCreateReqVO reqVO) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        String userIp = ServletUtils.getClientIP(request);
        return success(orderService.createOrder(memberId, userIp, reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "订单详情")
    @Parameter(name = "id", description = "订单ID", required = true)
    public CommonResult<AppOrderRespVO> getOrderDetail(@RequestParam("id") Long id) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        return success(orderService.getOrderDetail(id, memberId));
    }

    @GetMapping("/page")
    @Operation(summary = "我的订单分页")
    public CommonResult<PageResult<RestaurantOrderDO>> getOrderPage(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        return success(orderService.getOrderPage(memberId, pageNo, pageSize, status));
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消订单")
    @Parameter(name = "id", description = "订单ID", required = true)
    public CommonResult<Boolean> cancelOrder(@RequestParam("id") Long id) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        orderService.cancelOrder(id, memberId);
        return success(true);
    }

    @GetMapping("/count")
    @Operation(summary = "订单数量统计")
    public CommonResult<java.util.Map<String, Long>> getOrderCount() {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        return success(orderService.getOrderCount(memberId));
    }

}
