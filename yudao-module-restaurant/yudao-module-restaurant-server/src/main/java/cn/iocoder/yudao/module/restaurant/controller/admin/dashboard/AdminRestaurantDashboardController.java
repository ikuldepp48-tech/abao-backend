package cn.iocoder.yudao.module.restaurant.controller.admin.dashboard;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.dashboard.vo.DashboardRespVO;
import cn.iocoder.yudao.module.restaurant.service.dashboard.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 首页看板")
@RestController
@RequestMapping("/restaurant/dashboard")
@Validated
public class AdminRestaurantDashboardController {

    @Resource
    private DashboardService dashboardService;

    @GetMapping("/data")
    @Operation(summary = "获取首页看板数据")
    @ApiAccessLog(operateType = GET)
    public CommonResult<DashboardRespVO> getDashboard() {
        return success(dashboardService.getDashboard());
    }
}
