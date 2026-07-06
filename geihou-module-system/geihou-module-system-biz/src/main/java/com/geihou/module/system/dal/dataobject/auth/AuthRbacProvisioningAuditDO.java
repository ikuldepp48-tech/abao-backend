package com.geihou.module.system.dal.dataobject.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * Append-only audit row for RBAC provisioning attempts.
 *
 * <p>H148 RECOVERY DESIGN DECISION: no {@code deleted} column, no update/delete path.
 * The table records one row per attempt outcome (STARTED / SUCCEEDED / FAILED).
 */
@TableName("auth_rbac_provisioning_audit")
public class AuthRbacProvisioningAuditDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("attempt_id")
    private String attemptId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("merchant_type")
    private String merchantType;

    @TableField("outcome")
    private String outcome;

    @TableField("roles_expected")
    private Integer rolesExpected;

    @TableField("roles_inserted")
    private Integer rolesInserted;

    @TableField("roles_skipped")
    private Integer rolesSkipped;

    @TableField("grants_expected")
    private Integer grantsExpected;

    @TableField("grants_inserted")
    private Integer grantsInserted;

    @TableField("grants_skipped")
    private Integer grantsSkipped;

    @TableField("grants_preserved")
    private Integer grantsPreserved;

    @TableField("error_class")
    private String errorClass;

    @TableField("error_detail")
    private String errorDetail;

    @TableField("triggered_by")
    private String triggeredBy;

    @TableField("creator")
    private String creator;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("updater")
    private String updater;

    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String attemptId) { this.attemptId = attemptId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getMerchantType() { return merchantType; }
    public void setMerchantType(String merchantType) { this.merchantType = merchantType; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public Integer getRolesExpected() { return rolesExpected; }
    public void setRolesExpected(Integer rolesExpected) { this.rolesExpected = rolesExpected; }

    public Integer getRolesInserted() { return rolesInserted; }
    public void setRolesInserted(Integer rolesInserted) { this.rolesInserted = rolesInserted; }

    public Integer getRolesSkipped() { return rolesSkipped; }
    public void setRolesSkipped(Integer rolesSkipped) { this.rolesSkipped = rolesSkipped; }

    public Integer getGrantsExpected() { return grantsExpected; }
    public void setGrantsExpected(Integer grantsExpected) { this.grantsExpected = grantsExpected; }

    public Integer getGrantsInserted() { return grantsInserted; }
    public void setGrantsInserted(Integer grantsInserted) { this.grantsInserted = grantsInserted; }

    public Integer getGrantsSkipped() { return grantsSkipped; }
    public void setGrantsSkipped(Integer grantsSkipped) { this.grantsSkipped = grantsSkipped; }

    public Integer getGrantsPreserved() { return grantsPreserved; }
    public void setGrantsPreserved(Integer grantsPreserved) { this.grantsPreserved = grantsPreserved; }

    public String getErrorClass() { return errorClass; }
    public void setErrorClass(String errorClass) { this.errorClass = errorClass; }

    public String getErrorDetail() { return errorDetail; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }

    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getUpdater() { return updater; }
    public void setUpdater(String updater) { this.updater = updater; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
