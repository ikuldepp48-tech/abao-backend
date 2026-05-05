package cn.iocoder.yudao.module.consulting.service.dashboard;

import cn.iocoder.yudao.module.consulting.controller.admin.dashboard.vo.*;
import cn.iocoder.yudao.module.consulting.dal.dataobject.client.ConsultingClientContactDO;
import cn.iocoder.yudao.module.consulting.dal.dataobject.client.ConsultingClientDO;
import cn.iocoder.yudao.module.consulting.dal.dataobject.engagement.ConsultingEngagementDO;
import cn.iocoder.yudao.module.consulting.dal.dataobject.engagement.DeliverableItem;
import cn.iocoder.yudao.module.consulting.dal.mysql.client.ConsultingClientContactMapper;
import cn.iocoder.yudao.module.consulting.dal.mysql.client.ConsultingClientMapper;
import cn.iocoder.yudao.module.consulting.dal.mysql.engagement.ConsultingEngagementMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Validated
public class DashboardServiceImpl implements DashboardService {

    @Resource
    private ConsultingClientMapper clientMapper;
    @Resource
    private ConsultingEngagementMapper engagementMapper;
    @Resource
    private ConsultingClientContactMapper contactMapper;

    // ==================== 1. Summary ====================

    @Override
    public DashboardSummaryRespVO getSummary() {
        long totalClients = clientMapper.selectCount();
        List<ConsultingEngagementDO> allEngagements = engagementMapper.selectList();

        long activeCount = 0;
        long completedCount = 0;
        BigDecimal totalContract = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (ConsultingEngagementDO e : allEngagements) {
            if ("in_progress".equals(e.getStatus())) {
                activeCount++;
            } else if ("completed".equals(e.getStatus())) {
                completedCount++;
            }
            if (e.getContractAmount() != null) {
                totalContract = totalContract.add(e.getContractAmount());
            }
            if (e.getPaidAmount() != null) {
                totalPaid = totalPaid.add(e.getPaidAmount());
            }
        }

        return DashboardSummaryRespVO.builder()
                .totalClients(totalClients)
                .activeEngagements(activeCount)
                .completedEngagements(completedCount)
                .totalContractAmount(totalContract)
                .totalPaidAmount(totalPaid)
                .build();
    }

    // ==================== 2. Client Ranking ====================

    @Override
    public List<ClientRankingItemVO> getClientRanking() {
        List<ConsultingClientDO> clients = clientMapper.selectList();
        if (clients.isEmpty()) {
            return Collections.emptyList();
        }

        List<ClientRankingItemVO> ranking = new ArrayList<>();
        for (ConsultingClientDO client : clients) {
            int communicationScore = calcCommunicationScore(client.getId());
            int projectScore = calcProjectScore(client.getId());
            int contractScore = calcContractScore(client);
            int totalScore = communicationScore + projectScore + contractScore;

            ranking.add(ClientRankingItemVO.builder()
                    .clientId(client.getId())
                    .clientName(client.getName())
                    .score(totalScore)
                    .communicationScore(communicationScore)
                    .projectScore(projectScore)
                    .contractScore(contractScore)
                    .build());
        }

        // 按总分升序（最差的排最前）
        ranking.sort(Comparator.comparingInt(ClientRankingItemVO::getScore));
        return ranking;
    }

    /** 沟通频率分：每个联系人 10 分，上限 40 */
    private int calcCommunicationScore(Long clientId) {
        List<ConsultingClientContactDO> contacts = contactMapper.selectList(
                ConsultingClientContactDO::getClientId, clientId);
        return Math.min(contacts.size() * 10, 40);
    }

    /** 项目活跃分：取最新项目，按 currentPhase/6*30 计分 */
    private int calcProjectScore(Long clientId) {
        List<ConsultingEngagementDO> engagements = engagementMapper.selectList(
                ConsultingEngagementDO::getClientId, clientId);
        if (engagements.isEmpty()) {
            return 0;
        }
        // 取 ID 最大的（最新）
        ConsultingEngagementDO latest = engagements.stream()
                .max(Comparator.comparingLong(ConsultingEngagementDO::getId))
                .orElse(null);
        if (latest == null) {
            return 0;
        }
        if ("completed".equals(latest.getStatus())) {
            return 30;
        }
        if ("in_progress".equals(latest.getStatus()) && latest.getCurrentPhase() != null) {
            return (int) Math.round(latest.getCurrentPhase() / 6.0 * 30);
        }
        return 0;
    }

    /** 合同剩余分：按剩余天数比例计分 */
    private int calcContractScore(ConsultingClientDO client) {
        LocalDate startDate = client.getServiceStartDate();
        LocalDate endDate = client.getServiceEndDate();
        if (endDate == null) {
            return 15; // 未设置，默认中等
        }
        LocalDate today = LocalDate.now();
        if (endDate.isBefore(today)) {
            return 0;
        }
        long remaining = ChronoUnit.DAYS.between(today, endDate);
        long total = startDate != null ? ChronoUnit.DAYS.between(startDate, endDate) : 365;
        if (total <= 0) {
            total = 1;
        }
        return (int) Math.min(Math.round((double) remaining / total * 30), 30);
    }

    // ==================== 3. Urgent Todos ====================

    @Override
    public List<UrgentTodoItemVO> getUrgentTodos() {
        List<UrgentTodoItemVO> todos = new ArrayList<>();

        // 加载所有客户（用于查询客户名称）
        Map<Long, ConsultingClientDO> clientMap = clientMapper.selectList().stream()
                .collect(Collectors.toMap(ConsultingClientDO::getId, c -> c));

        todos.addAll(findContractExpiring(clientMap));
        todos.addAll(findPhaseStalled(clientMap));
        todos.addAll(findDeliverableDue(clientMap));

        // 按优先级排序：high 在前
        todos.sort(Comparator.comparing(t -> "high".equals(t.getPriority()) ? 0 : 1));
        return todos;
    }

    /** 合同即将到期：serviceEndDate 在未来 30 天内 */
    private List<UrgentTodoItemVO> findContractExpiring(Map<Long, ConsultingClientDO> clientMap) {
        List<UrgentTodoItemVO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(30);

        for (ConsultingClientDO client : clientMap.values()) {
            LocalDate endDate = client.getServiceEndDate();
            if (endDate == null || endDate.isBefore(today) || endDate.isAfter(threshold)) {
                continue;
            }
            long remainingDays = ChronoUnit.DAYS.between(today, endDate);
            result.add(UrgentTodoItemVO.builder()
                    .type("contract_expiring")
                    .title(client.getName() + " 服务合同即将到期")
                    .clientId(client.getId())
                    .clientName(client.getName())
                    .priority("high")
                    .deadline(endDate)
                    .detail("合同将于 " + remainingDays + " 天后到期，请及时续约")
                    .build());
        }
        return result;
    }

    /** 项目阶段停滞：最后完成阶段超过 14 天未推进 */
    private List<UrgentTodoItemVO> findPhaseStalled(Map<Long, ConsultingClientDO> clientMap) {
        List<UrgentTodoItemVO> result = new ArrayList<>();
        List<ConsultingEngagementDO> engagements = engagementMapper.selectList(
                ConsultingEngagementDO::getStatus, "in_progress");
        LocalDateTime threshold = LocalDateTime.now().minusDays(14);

        for (ConsultingEngagementDO e : engagements) {
            Integer phase = e.getCurrentPhase();
            if (phase == null || phase == 0) {
                continue; // 还没开始推进
            }
            LocalDateTime lastDone = getPhaseDoneTime(e, phase);
            if (lastDone == null || lastDone.isAfter(threshold)) {
                continue;
            }
            long daysStalled = ChronoUnit.DAYS.between(lastDone.toLocalDate(), LocalDate.now());
            String clientName = clientMap.containsKey(e.getClientId())
                    ? clientMap.get(e.getClientId()).getName() : "未知客户";
            result.add(UrgentTodoItemVO.builder()
                    .type("phase_stalled")
                    .title("项目「" + e.getTitle() + "」阶段停滞")
                    .clientId(e.getClientId())
                    .clientName(clientName)
                    .priority("medium")
                    .deadline(LocalDate.now().plusDays(7))
                    .detail("当前阶段 " + phase + " 已停滞 " + daysStalled + " 天，请尽快推进下一阶段")
                    .build());
        }
        return result;
    }

    /** 交付物即将到期：dueDate 在未来 7 天内且未完成 */
    private List<UrgentTodoItemVO> findDeliverableDue(Map<Long, ConsultingClientDO> clientMap) {
        List<UrgentTodoItemVO> result = new ArrayList<>();
        List<ConsultingEngagementDO> engagements = engagementMapper.selectList(
                ConsultingEngagementDO::getStatus, "in_progress");
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(7);

        for (ConsultingEngagementDO e : engagements) {
            List<DeliverableItem> deliverables = e.getDeliverables();
            if (deliverables == null || deliverables.isEmpty()) {
                continue;
            }
            for (DeliverableItem item : deliverables) {
                if (item.getDueDate() == null) {
                    continue;
                }
                if ("accepted".equals(item.getStatus())) {
                    continue;
                }
                if (item.getDueDate().isBefore(today) || item.getDueDate().isAfter(threshold)) {
                    continue;
                }
                String clientName = clientMap.containsKey(e.getClientId())
                        ? clientMap.get(e.getClientId()).getName() : "未知客户";
                long remaining = ChronoUnit.DAYS.between(today, item.getDueDate());
                result.add(UrgentTodoItemVO.builder()
                        .type("deliverable_due")
                        .title("交付物「" + item.getName() + "」即将到期")
                        .clientId(e.getClientId())
                        .clientName(clientName)
                        .priority("high")
                        .deadline(item.getDueDate())
                        .detail("项目「" + e.getTitle() + "」的交付物，距截止日还有 " + remaining + " 天，当前状态：" + item.getStatus())
                        .build());
            }
        }
        return result;
    }

    // ==================== 4. Week Schedule ====================

    @Override
    public List<WeekScheduleItemVO> getWeekSchedule() {
        List<WeekScheduleItemVO> schedule = new ArrayList<>();

        // 本周区间：周一 00:00 → 下周一 00:00（左闭右开）
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        LocalDateTime weekStart = monday.atStartOfDay();
        LocalDateTime weekEnd = sunday.plusDays(1).atStartOfDay();

        // 加载所有客户 ID→名称 映射
        Map<Long, ConsultingClientDO> clientMap = clientMapper.selectList().stream()
                .collect(Collectors.toMap(ConsultingClientDO::getId, c -> c));

        List<ConsultingEngagementDO> engagements = engagementMapper.selectList();

        for (ConsultingEngagementDO e : engagements) {
            String clientName = clientMap.containsKey(e.getClientId())
                    ? clientMap.get(e.getClientId()).getName() : "未知客户";

            // 阶段完成时间
            schedule.addAll(collectPhaseCompletions(e, clientName, weekStart, weekEnd));
            // 项目起止日期
            schedule.addAll(collectEngagementDates(e, clientName, monday, sunday));
            // 交付物截止日
            schedule.addAll(collectDeliverableDeadlines(e, clientName, monday, sunday));
        }

        // 按日期+时间排序
        schedule.sort(Comparator.comparing(WeekScheduleItemVO::getDate)
                .thenComparing(WeekScheduleItemVO::getTime, Comparator.nullsLast(Comparator.naturalOrder())));
        return schedule;
    }

    /** 收集本周内的阶段完成事件 */
    private List<WeekScheduleItemVO> collectPhaseCompletions(ConsultingEngagementDO e, String clientName,
                                                              LocalDateTime weekStart, LocalDateTime weekEnd) {
        List<WeekScheduleItemVO> items = new ArrayList<>();
        String[] phaseNames = {
                "项目启动+共识锚定", "调研诊断+数据采集", "方案设计+策略输出",
                "资源对接+系统落地", "试跑验收+效果评估", "落地跟进+效果迭代"
        };
        LocalDateTime[] phaseTimes = {
                e.getPhase1DoneAt(), e.getPhase2DoneAt(), e.getPhase3DoneAt(),
                e.getPhase4DoneAt(), e.getPhase5DoneAt(), e.getPhase6DoneAt()
        };

        for (int i = 0; i < phaseTimes.length; i++) {
            LocalDateTime pt = phaseTimes[i];
            if (pt == null || pt.isBefore(weekStart) || !pt.isBefore(weekEnd)) {
                continue;
            }
            int phaseNum = i + 1;
            items.add(WeekScheduleItemVO.builder()
                    .date(pt.toLocalDate())
                    .time(pt)
                    .type("phase_completed")
                    .title("阶段" + phaseNum + "完成：" + phaseNames[i])
                    .clientId(e.getClientId())
                    .clientName(clientName)
                    .engagementId(e.getId())
                    .engagementTitle(e.getTitle())
                    .build());
        }
        return items;
    }

    /** 收集本周内的项目起止日期 */
    private List<WeekScheduleItemVO> collectEngagementDates(ConsultingEngagementDO e, String clientName,
                                                             LocalDate monday, LocalDate sunday) {
        List<WeekScheduleItemVO> items = new ArrayList<>();
        if (e.getStartDate() != null && !e.getStartDate().isBefore(monday)
                && !e.getStartDate().isAfter(sunday)) {
            items.add(WeekScheduleItemVO.builder()
                    .date(e.getStartDate())
                    .time(e.getStartDate().atStartOfDay())
                    .type("engagement_start")
                    .title("项目启动：" + e.getTitle())
                    .clientId(e.getClientId())
                    .clientName(clientName)
                    .engagementId(e.getId())
                    .engagementTitle(e.getTitle())
                    .build());
        }
        if (e.getEndDate() != null && !e.getEndDate().isBefore(monday)
                && !e.getEndDate().isAfter(sunday)) {
            items.add(WeekScheduleItemVO.builder()
                    .date(e.getEndDate())
                    .time(e.getEndDate().atStartOfDay())
                    .type("engagement_end")
                    .title("项目计划结束：" + e.getTitle())
                    .clientId(e.getClientId())
                    .clientName(clientName)
                    .engagementId(e.getId())
                    .engagementTitle(e.getTitle())
                    .build());
        }
        return items;
    }

    /** 收集本周内的交付物截止日 */
    private List<WeekScheduleItemVO> collectDeliverableDeadlines(ConsultingEngagementDO e, String clientName,
                                                                  LocalDate monday, LocalDate sunday) {
        List<WeekScheduleItemVO> items = new ArrayList<>();
        List<DeliverableItem> deliverables = e.getDeliverables();
        if (deliverables == null || deliverables.isEmpty()) {
            return items;
        }
        for (DeliverableItem item : deliverables) {
            if (item.getDueDate() == null || item.getDueDate().isBefore(monday)
                    || item.getDueDate().isAfter(sunday)) {
                continue;
            }
            items.add(WeekScheduleItemVO.builder()
                    .date(item.getDueDate())
                    .time(item.getDueDate().atStartOfDay())
                    .type("deliverable_due")
                    .title("交付物截止：" + item.getName())
                    .clientId(e.getClientId())
                    .clientName(clientName)
                    .engagementId(e.getId())
                    .engagementTitle(e.getTitle())
                    .build());
        }
        return items;
    }

    // ==================== Helper ====================

    private LocalDateTime getPhaseDoneTime(ConsultingEngagementDO engagement, int phase) {
        return switch (phase) {
            case 1 -> engagement.getPhase1DoneAt();
            case 2 -> engagement.getPhase2DoneAt();
            case 3 -> engagement.getPhase3DoneAt();
            case 4 -> engagement.getPhase4DoneAt();
            case 5 -> engagement.getPhase5DoneAt();
            case 6 -> engagement.getPhase6DoneAt();
            default -> null;
        };
    }

}
