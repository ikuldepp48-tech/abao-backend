package com.geihou.module.supplychain.stock.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Data object for supplychain_command_journal table.
 *
 * <p>C0 (G0-04H185-FIN-CONTRACT-REVISION-C0) introduced the command-status endpoint;
 * FIN-CONSISTENCY implements the journal infrastructure. Records the execution
 * result of finance->supplychain write commands. INSERT-only, no status/INFLIGHT
 * field, no logic delete, no business table FK.
 */
@TableName("supplychain_command_journal")
public class SupplychainCommandJournalDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String operation;
    private String businessCommandId;
    private String requestBodySha256;
    private Integer resultSchemaVersion;
    private String resultSnapshot;
    private LocalDateTime executedAt;
    private LocalDateTime createTime;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public String getBusinessCommandId() { return businessCommandId; }
    public void setBusinessCommandId(String businessCommandId) { this.businessCommandId = businessCommandId; }

    public String getRequestBodySha256() { return requestBodySha256; }
    public void setRequestBodySha256(String requestBodySha256) { this.requestBodySha256 = requestBodySha256; }

    public Integer getResultSchemaVersion() { return resultSchemaVersion; }
    public void setResultSchemaVersion(Integer resultSchemaVersion) { this.resultSchemaVersion = resultSchemaVersion; }

    public String getResultSnapshot() { return resultSnapshot; }
    public void setResultSnapshot(String resultSnapshot) { this.resultSnapshot = resultSnapshot; }

    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
