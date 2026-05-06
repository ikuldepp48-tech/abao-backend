package cn.iocoder.yudao.module.consulting.controller.admin.dashboard;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.consulting.controller.admin.dashboard.vo.*;
import cn.iocoder.yudao.module.consulting.service.dashboard.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 咨询师工作台")
@RestController
@RequestMapping("/consulting/dashboard")
@Validated
public class DashboardController {

    @Resource
    private DashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(summary = "获取工作台摘要统计")
    @PreAuthorize("@ss.hasPermission('consulting:client:query')")
    public CommonResult<DashboardSummaryRespVO> getSummary() {
        return success(dashboardService.getSummary());
    }

    @GetMapping("/client-ranking")
    @Operation(summary = "获取客户健康度排行")
    @PreAuthorize("@ss.hasPermission('consulting:client:query')")
    public CommonResult<List<ClientRankingItemVO>> getClientRanking() {
        return success(dashboardService.getClientRanking());
    }

    @GetMapping("/urgent-todos")
    @Operation(summary = "获取紧急待办列表")
    @PreAuthorize("@ss.hasPermission('consulting:client:query')")
    public CommonResult<List<UrgentTodoItemVO>> getUrgentTodos() {
        return success(dashboardService.getUrgentTodos());
    }

    @GetMapping("/week-schedule")
    @Operation(summary = "获取本周日程时间线")
    @PreAuthorize("@ss.hasPermission('consulting:client:query')")
    public CommonResult<List<WeekScheduleItemVO>> getWeekSchedule() {
        return success(dashboardService.getWeekSchedule());
    }

    @GetMapping("/client-panorama")
    @Operation(summary = "获取客户全景视图（所有客户含健康度）")
    @PreAuthorize("@ss.hasPermission('consulting:client:query')")
    public CommonResult<List<ClientPanoramaRespVO>> getClientPanorama() {
        return success(dashboardService.getClientPanorama());
    }

}
