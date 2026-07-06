package com.geihou.module.supplychain.stock.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.stock.dal.dataobject.StockMappingCoverageAuditDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper for stock_mapping_coverage_audit.
 */
public interface StockMappingCoverageAuditMapper extends BaseMapperX<StockMappingCoverageAuditDO> {

    @Select("SELECT * FROM stock_mapping_coverage_audit WHERE tenant_id = #{tenantId} " +
            "AND coverage_type = #{coverageType} AND source_module = #{sourceModule} " +
            "AND source_record_id = #{sourceRecordId} AND idempotent_key = #{idempotentKey} " +
            "AND deleted = false")
    StockMappingCoverageAuditDO selectLogical(@Param("tenantId") Long tenantId,
                                               @Param("coverageType") String coverageType,
                                               @Param("sourceModule") String sourceModule,
                                               @Param("sourceRecordId") Long sourceRecordId,
                                               @Param("idempotentKey") String idempotentKey);

    @Update("UPDATE stock_mapping_coverage_audit SET mode = #{mode}, last_seen_time = #{lastSeenTime}, " +
            "seen_count = seen_count + 1, updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = false")
    int incrementSeen(@Param("id") Long id,
                      @Param("tenantId") Long tenantId,
                      @Param("mode") String mode,
                      @Param("lastSeenTime") LocalDateTime lastSeenTime,
                      @Param("updater") String updater,
                      @Param("updateTime") LocalDateTime updateTime);

    @Select("SELECT * FROM stock_mapping_coverage_audit WHERE tenant_id = #{tenantId} " +
            "AND deleted = false ORDER BY last_seen_time DESC LIMIT #{limit}")
    List<StockMappingCoverageAuditDO> selectRecentByTenant(@Param("tenantId") Long tenantId,
                                                            @Param("limit") int limit);

    /**
     * Select all (non-deleted) audit rows for a tenant, ordered by last_seen_time descending.
     * Used by the read-only coverage report service (G2-01B3C).
     */
    @Select("SELECT * FROM stock_mapping_coverage_audit WHERE tenant_id = #{tenantId} " +
            "AND deleted = false ORDER BY last_seen_time DESC")
    List<StockMappingCoverageAuditDO> selectAllByTenant(@Param("tenantId") Long tenantId);
}
