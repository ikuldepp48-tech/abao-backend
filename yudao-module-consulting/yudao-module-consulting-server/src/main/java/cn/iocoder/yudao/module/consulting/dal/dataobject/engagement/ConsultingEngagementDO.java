package cn.iocoder.yudao.module.consulting.dal.dataobject.engagement;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 咨询项目 DO
 */
@TableName(value = "consulting_engagement", autoResultMap = true)
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultingEngagementDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 关联咨询业务租户 ID
     */
    private Long tenantId;

    /**
     * 关联客户 ID
     */
    private Long clientId;

    /**
     * 咨询师用户 ID
     */
    private Long consultantUserId;

    /**
     * 项目编号（如 ENG-2026-001）
     */
    private String code;

    /**
     * 项目名称
     */
    private String title;

    /**
     * 项目类型（字典 consulting_engagement_type）
     */
    private String type;

    /**
     * 项目状态（字典 consulting_engagement_status）
     */
    private String status;

    /**
     * 项目开始日期
     */
    private LocalDate startDate;

    /**
     * 计划结束日期
     */
    private LocalDate endDate;

    /**
     * 实际结束日期
     */
    private LocalDate actualEndDate;

    /**
     * 合同金额（万元）
     */
    private BigDecimal contractAmount;

    /**
     * 已收款（万元）
     */
    private BigDecimal paidAmount;

    /**
     * 当前阶段（1-6）
     */
    private Integer currentPhase;

    private LocalDateTime phase1DoneAt;
    private LocalDateTime phase2DoneAt;
    private LocalDateTime phase3DoneAt;
    private LocalDateTime phase4DoneAt;
    private LocalDateTime phase5DoneAt;
    private LocalDateTime phase6DoneAt;

    /**
     * 项目目标
     */
    private String objectives;

    /**
     * 服务范围
     */
    private String scope;

    /**
     * 边界约束
     */
    private String constraints;

    /**
     * 交付物清单（JSON 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<DeliverableItem> deliverables;

}
