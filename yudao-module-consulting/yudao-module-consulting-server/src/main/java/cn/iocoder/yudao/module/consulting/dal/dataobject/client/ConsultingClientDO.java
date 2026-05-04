package cn.iocoder.yudao.module.consulting.dal.dataobject.client;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 咨询客户档案 DO
 */
@TableName("consulting_client")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultingClientDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 客户对应的 yudao 租户 ID
     */
    private Long tenantId;

    /**
     * 服务该客户的咨询师用户 ID
     */
    private Long consultantUserId;

    /**
     * 客户公司名
     */
    private String name;

    /**
     * 简称
     */
    private String shortName;

    /**
     * 行业（字典 consulting_industry）
     */
    private String industry;

    /**
     * 客户状态（字典 consulting_client_status）
     */
    private Integer status;

    /**
     * 法人姓名
     */
    private String legalPerson;

    /**
     * 成立时间
     */
    private LocalDate foundedDate;

    /**
     * 注册资本（万元）
     */
    private BigDecimal registeredCapital;

    /**
     * 员工规模（字典 consulting_employee_range）
     */
    private String employeeCountRange;

    /**
     * 月营收区间（字典 consulting_revenue_range）
     */
    private String monthlyRevenueRange;

    /**
     * 门店数
     */
    private Integer storeCount;

    /**
     * 业务模式（字典）
     */
    private String businessModel;

    /**
     * POS 系统（字典）
     */
    private String posSystem;

    /**
     * 财务系统（字典）
     */
    private String financeSystem;

    /**
     * 会员系统（字典）
     */
    private String crmSystem;

    /**
     * IT 系统补充说明
     */
    private String itRemark;

    /**
     * 服务开始日期
     */
    private LocalDate serviceStartDate;

    /**
     * 服务结束日期
     */
    private LocalDate serviceEndDate;

    /**
     * 服务类型（字典）
     */
    private String serviceType;

    /**
     * 年度服务费（万元）
     */
    private BigDecimal annualFee;

    /**
     * 服务状态（字典）
     */
    private Integer serviceStatus;

    /**
     * 客户主要痛点
     */
    private String keyPainPoints;

    /**
     * 客户核心诉求
     */
    private String coreDemands;

    /**
     * 补充说明
     */
    private String remark;

}
