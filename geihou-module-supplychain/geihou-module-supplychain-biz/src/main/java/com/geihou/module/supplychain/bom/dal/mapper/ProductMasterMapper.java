package com.geihou.module.supplychain.bom.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Mapper for product_master table.
 *
 * <p>Product master data CRUD, tenant-isolated.
 */
public interface ProductMasterMapper extends BaseMapperX<ProductMasterDO> {

    /**
     * Find by tenant_id + product_code (unique key).
     */
    @Select("SELECT * FROM product_master WHERE tenant_id = #{tenantId} " +
            "AND product_code = #{productCode} AND deleted = false")
    ProductMasterDO selectByTenantProductCode(@Param("tenantId") Long tenantId,
                                               @Param("productCode") String productCode);

    /**
     * Find by id + tenant_id (tenant isolation).
     */
    @Select("SELECT * FROM product_master WHERE id = #{id} " +
            "AND tenant_id = #{tenantId} AND deleted = false")
    ProductMasterDO selectByIdAndTenant(@Param("id") Long id,
                                         @Param("tenantId") Long tenantId);

    /**
     * List all products for a tenant.
     */
    @Select("SELECT * FROM product_master WHERE tenant_id = #{tenantId} " +
            "AND deleted = false ORDER BY id")
    List<ProductMasterDO> listByTenant(@Param("tenantId") Long tenantId);

    /**
     * Update is_active status by id + tenant_id (tenant-isolated).
     */
    @Update("UPDATE product_master SET is_active = #{isActive}, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = false")
    int updateActiveByIdAndTenant(@Param("id") Long id,
                                   @Param("tenantId") Long tenantId,
                                   @Param("isActive") Boolean isActive,
                                   @Param("updater") String updater,
                                   @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * Update mutable fields by id + tenant_id (tenant-isolated).
     * product_code and product_type are immutable and excluded.
     */
    @Update("UPDATE product_master SET product_name = #{productName}, " +
            "sku_code = #{skuCode}, unit = #{unit}, category = #{category}, " +
            "description = #{description}, updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = false")
    int updateMutableByIdAndTenant(@Param("id") Long id,
                                    @Param("tenantId") Long tenantId,
                                    @Param("productName") String productName,
                                    @Param("skuCode") String skuCode,
                                    @Param("unit") String unit,
                                    @Param("category") String category,
                                    @Param("description") String description,
                                    @Param("updater") String updater,
                                    @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * 按 tenantId + skuCode 查询 active 状态的 finished 商品列表。
     *
     * <p>返回 List 以显式区分 0 / 1 / 多条结果，由调用方决定多结果策略。
     * 此方法为只读查询，不写入任何数据。
     *
     * <p>SQL 约束（五项全部必须）：
     * <ul>
     *   <li>{@code tenant_id = #{tenantId}} — 租户隔离</li>
     *   <li>{@code sku_code = #{skuCode}} — 精确匹配 SKU 编码</li>
     *   <li>{@code deleted = false} — 排除已删除记录</li>
     *   <li>{@code is_active = true} — 只查 active 状态商品</li>
     *   <li>{@code product_type = 'FINISHED'} — 只查成品</li>
     * </ul>
     *
     * <p>Source: TASK-G2-02H-2A.
     *
     * @param tenantId 租户 ID（不可为 null）
     * @param skuCode  SKU 编码（不可为 null/blank）
     * @return 匹配的商品 DO 列表，可能为空列表，不会为 null
     */
    @Select("SELECT * FROM product_master WHERE tenant_id = #{tenantId} " +
            "AND sku_code = #{skuCode} " +
            "AND deleted = false " +
            "AND is_active = true " +
            "AND product_type = 'FINISHED'")
    List<ProductMasterDO> selectActiveFinishedByTenantSkuCode(
            @Param("tenantId") Long tenantId,
            @Param("skuCode") String skuCode
    );
}
