package com.geihou.module.supplychain.stock.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.stock.dal.dataobject.SupplychainCommandJournalDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Mapper for supplychain_command_journal table.
 *
 * <p>C0 (G0-04H185-FIN-CONTRACT-REVISION-C0) introduced the command-status endpoint;
 * FIN-CONSISTENCY implements the journal infrastructure. Provides tenant-scoped
 * queries for command journal records. All queries must include tenantId - no bypass.
 *
 * <p>Journal is INSERT-only (success snapshots). T2 loser recovery uses regular
 * queries with bounded retry to read the winner's committed journal; no pessimistic
 * locking or SELECT ... FOR UPDATE is authorized.
 */
public interface SupplychainCommandJournalMapper extends BaseMapperX<SupplychainCommandJournalDO> {

    /**
     * Find journal by tenant_id + operation + businessCommandId.
     */
    @Select("SELECT * FROM supplychain_command_journal WHERE tenant_id = #{tenantId} " +
            "AND operation = #{operation} AND business_command_id = #{businessCommandId}")
    SupplychainCommandJournalDO selectByTenantOperationCommandId(
            @Param("tenantId") Long tenantId,
            @Param("operation") String operation,
            @Param("businessCommandId") String businessCommandId);
}
