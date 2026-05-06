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
        int totalScoreSum = 0;

        for (ConsultingClientDO client : clients) {
            int communicationScore = calcCommunicationScore(client.getId());
            int projectScore = calcProjectScore(client.getId());
            int contractScore = calcContractScore(client);
            int totalScore = communicationScore + projectScore + contractScore;
            totalScoreSum += totalScore;

            // 健康度等级
            String healthLevel;
            if (totalScore >= 70) {
                healthLevel = "green";
            } else if (totalScore >= 40) {
                healthLevel = "yellow";
            } else {
                healthLevel = "red";
            }

            // 预警原因
            List<String> warnings = new ArrayList<>();
            if (communicationScore < 10) {
                warnings.add("30天未沟通");
            }
            if (projectScore < 10) {
                warnings.add("项目停滞");
            }
            if (contractScore < 10) {
                warnings.add("合同即将到期");
            }

            // 紧急操作建议
            String urgentAction = "";
            if (totalScore < 40) {
                urgentAction = "立即跟进";
            }

            ranking.add(ClientRankingItemVO.builder()
                    .clientId(client.getId())
                    .clientName(client.getName())
                    .score(totalScore)
                    .communicationScore(communicationScore)
                    .projectScore(projectScore)
                    .contractScore(contractScore)
                    .healthLevel(healthLevel)
                    .warnings(warnings)
                    .urgentAction(urgentAction)
                    .build());
        }

        // 计算平均分
        int avgScore = ranking.isEmpty() ? 0 : totalScoreSum / ranking.size();
        for (ClientRankingItemVO item : ranking) {
            item.setAvgScore(avgScore);
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

        // 按优先级排序：critical > warning > info
        todos.sort(Comparator.comparingInt(t -> {
            return switch (t.getPriority()) {
                case "critical" -> 0;
                case "warning" -> 1;
                default -> 2;
            };
        }));
        return todos;
    }

    /** 合同即将到期：按剩余天数分级 */
    private List<UrgentTodoItemVO> findContractExpiring(Map<Long, ConsultingClientDO> clientMap) {
        List<UrgentTodoItemVO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (ConsultingClientDO client : clientMap.values()) {
            LocalDate endDate = client.getServiceEndDate();
            if (endDate == null || endDate.isBefore(today)) {
                continue;
            }
            long daysRemaining = ChronoUnit.DAYS.between(today, endDate);
            if (daysRemaining > 30) {
                continue;
            }

            String priority;
            String detail;
            List<String> actions = new ArrayList<>();
            actions.add("续约");

            if (daysRemaining <= 7) {
                priority = "critical";
                detail = "合同将于 " + daysRemaining + " 天后到期，必须本周处理";
                actions.add("终止");
            } else if (daysRemaining <= 14) {
                priority = "warning";
                detail = "合同将于 " + daysRemaining + " 天后到期，请本周内关注";
            } else {
                priority = "info";
                detail = "合同将于 " + daysRemaining + " 天后到期，提前准备续约";
            }

            result.add(UrgentTodoItemVO.builder()
                    .type("contract_expiring")
                    .title(client.getName() + " 服务合同即将到期")
                    .clientId(client.getId())
                    .clientName(client.getName())
                    .priority(priority)
                    .deadline(endDate)
                    .daysRemaining((int) daysRemaining)
                    .detail(detail)
                    .actions(actions)
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
                continue;
            }
            LocalDateTime lastDone = getPhaseDoneTime(e, phase);
            if (lastDone == null || lastDone.isAfter(threshold)) {
                continue;
            }
            long daysStalled = ChronoUnit.DAYS.between(lastDone.toLocalDate(), LocalDate.now());
            String clientName = clientMap.containsKey(e.getClientId())
                    ? clientMap.get(e.getClientId()).getName() : "未知客户";

            String priority;
            List<String> actions = new ArrayList<>();
            actions.add("推进阶段");
            if (daysStalled > 30) {
                priority = "critical";
            } else if (daysStalled > 14) {
                priority = "warning";
            } else {
                priority = "info";
            }

            result.add(UrgentTodoItemVO.builder()
                    .type("phase_stalled")
                    .title("项目「" + e.getTitle() + "」阶段停滞")
                    .clientId(e.getClientId())
                    .clientName(clientName)
                    .priority(priority)
                    .deadline(LocalDate.now().plusDays(7))
                    .daysRemaining(7)
                    .detail("当前阶段 " + phase + " 已停滞 " + daysStalled + " 天，请尽快推进")
                    .actions(actions)
                    .build());
        }
        return result;
    }

    /** 交付物即将到期：按剩余天数分级 */
    private List<UrgentTodoItemVO> findDeliverableDue(Map<Long, ConsultingClientDO> clientMap) {
        List<UrgentTodoItemVO> result = new ArrayList<>();
        List<ConsultingEngagementDO> engagements = engagementMapper.selectList(
                ConsultingEngagementDO::getStatus, "in_progress");
        LocalDate today = LocalDate.now();

        for (ConsultingEngagementDO e : engagements) {
            List<DeliverableItem> deliverables = e.getDeliverables();
            if (deliverables == null || deliverables.isEmpty()) {
                continue;
            }
            for (DeliverableItem item : deliverables) {
                if (item.getDueDate() == null || "accepted".equals(item.getStatus())) {
                    continue;
                }
                String clientName = clientMap.containsKey(e.getClientId())
                        ? clientMap.get(e.getClientId()).getName() : "未知客户";
                long daysRemaining = ChronoUnit.DAYS.between(today, item.getDueDate());

                // 30天以内的交付物
                if (Math.abs(daysRemaining) > 30 && daysRemaining > 0) {
                    continue;
                }

                String priority;
                List<String> actions = new ArrayList<>();
                actions.add("标记完成");

                if (daysRemaining <= 0) {
                    priority = "critical";
                    actions.add("延期");
                } else if (daysRemaining <= 7) {
                    priority = "critical";
                    actions.add("延期");
                } else if (daysRemaining <= 14) {
                    priority = "warning";
                } else {
                    priority = "info";
                }

                result.add(UrgentTodoItemVO.builder()
                        .type("deliverable_due")
                        .title("交付物「" + item.getName() + "」即将到期")
                        .clientId(e.getClientId())
                        .clientName(clientName)
                        .priority(priority)
                        .deadline(item.getDueDate())
                        .daysRemaining((int) daysRemaining)
                        .detail("项目「" + e.getTitle() + "」，当前状态：" + item.getStatus())
                        .actions(actions)
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

    // ==================== 5. Client Panorama ====================

    @Override
    public List<ClientPanoramaRespVO> getClientPanorama() {
        List<ConsultingClientDO> clients = clientMapper.selectList();
        if (clients.isEmpty()) {
            return Collections.emptyList();
        }

        // 计算平均分
        int totalScoreSum = 0;
        for (ConsultingClientDO client : clients) {
            totalScoreSum += calcCommunicationScore(client.getId())
                    + calcProjectScore(client.getId())
                    + calcContractScore(client);
        }
        int avgScore = totalScoreSum / clients.size();

        // 获取所有项目
        List<ConsultingEngagementDO> allEngagements = engagementMapper.selectList();
        Map<Long, List<ConsultingEngagementDO>> engagementByClient = allEngagements.stream()
                .collect(Collectors.groupingBy(ConsultingEngagementDO::getClientId));

        List<ClientPanoramaRespVO> result = new ArrayList<>();
        for (ConsultingClientDO client : clients) {
            int score = calcCommunicationScore(client.getId())
                    + calcProjectScore(client.getId())
                    + calcContractScore(client);

            String healthLevel;
            if (score >= 70) {
                healthLevel = "green";
            } else if (score >= 40) {
                healthLevel = "yellow";
            } else {
                healthLevel = "red";
            }

            List<ConsultingEngagementDO> clientEngagements = engagementByClient.getOrDefault(
                    client.getId(), Collections.emptyList());
            int activeCount = (int) clientEngagements.stream()
                    .filter(e -> "in_progress".equals(e.getStatus())).count();
            List<String> titles = clientEngagements.stream()
                    .map(ConsultingEngagementDO::getTitle)
                    .collect(Collectors.toList());

            result.add(ClientPanoramaRespVO.builder()
                    .clientId(client.getId())
                    .clientName(client.getName())
                    .shortName(client.getShortName())
                    .industry(client.getIndustry())
                    .score(score)
                    .healthLevel(healthLevel)
                    .activeEngagementCount(activeCount)
                    .engagementTitles(titles)
                    .storeCount(client.getStoreCount())
                    .status(client.getStatus())
                    .avgScore(avgScore)
                    .build());
        }

        // 按健康分升序
        result.sort(Comparator.comparingInt(ClientPanoramaRespVO::getScore));
        return result;
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
