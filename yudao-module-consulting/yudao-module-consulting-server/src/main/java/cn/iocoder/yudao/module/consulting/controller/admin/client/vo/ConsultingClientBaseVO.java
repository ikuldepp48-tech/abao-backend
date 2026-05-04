package cn.iocoder.yudao.module.consulting.controller.admin.client.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 咨询客户档案 Base VO
 */
@Data
public class ConsultingClientBaseVO {

    @Schema(description = "客户公司名", requiredMode = Schema.RequiredMode.REQUIRED, example = "阿堡餐饮")
    @NotEmpty(message = "客户公司名不能为空")
    private String name;

    @Schema(description = "简称", example = "阿堡")
    private String shortName;

    @Schema(description = "行业（字典 consulting_industry）", example = "catering")
    private String industry;

    @Schema(description = "客户状态（字典 consulting_client_status）", example = "1")
    private Integer status;

    @Schema(description = "服务咨询师用户 ID", example = "1")
    private Long consultantUserId;

    // ========== 画像信息 ==========

    @Schema(description = "法人姓名", example = "张三")
    private String legalPerson;

    @Schema(description = "成立时间")
    private LocalDate foundedDate;

    @Schema(description = "注册资本（万元）", example = "100.00")
    private BigDecimal registeredCapital;

    @Schema(description = "员工规模（字典 consulting_employee_range）", example = "11-50")
    private String employeeCountRange;

    @Schema(description = "月营收区间（字典 consulting_revenue_range）", example = "10-50万")
    private String monthlyRevenueRange;

    @Schema(description = "门店数", example = "5")
    private Integer storeCount;

    @Schema(description = "业务模式", example = "直营")
    private String businessModel;

    // ========== IT 系统 ==========

    @Schema(description = "POS 系统（字典 consulting_pos_system）", example = "meituan")
    private String posSystem;

    @Schema(description = "财务系统（字典 consulting_finance_system）", example = "yongyou")
    private String financeSystem;

    @Schema(description = "会员系统", example = "自研")
    private String crmSystem;

    @Schema(description = "IT 系统补充说明")
    private String itRemark;

    // ========== 服务信息 ==========

    @Schema(description = "服务开始日期")
    private LocalDate serviceStartDate;

    @Schema(description = "服务结束日期")
    private LocalDate serviceEndDate;

    @Schema(description = "服务类型（字典 consulting_service_type）", example = "annual")
    private String serviceType;

    @Schema(description = "年度服务费（万元）", example = "12.00")
    private BigDecimal annualFee;

    @Schema(description = "服务状态", example = "2")
    private Integer serviceStatus;

    // ========== 备注 ==========

    @Schema(description = "客户主要痛点")
    private String keyPainPoints;

    @Schema(description = "客户核心诉求")
    private String coreDemands;

    @Schema(description = "补充说明")
    private String remark;

}
