package cn.iocoder.yudao.module.consulting.service.dashboard;

import cn.iocoder.yudao.module.consulting.controller.admin.dashboard.vo.*;

import java.util.List;

/**
 * 咨询师工作台 Dashboard Service
 */
public interface DashboardService {

    /**
     * 获取工作台摘要统计
     */
    DashboardSummaryRespVO getSummary();

    /**
     * 获取客户健康度排行（按分数升序，最差的排最前）
     */
    List<ClientRankingItemVO> getClientRanking();

    /**
     * 获取紧急待办列表
     */
    List<UrgentTodoItemVO> getUrgentTodos();

    /**
     * 获取本周日程时间线
     */
    List<WeekScheduleItemVO> getWeekSchedule();

}
