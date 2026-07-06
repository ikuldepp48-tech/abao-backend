package com.geihou.module.finance.api.product;

import com.geihou.module.finance.api.product.dto.AddonGroupRespDTO;
import com.geihou.module.finance.api.product.dto.ComboItemRespDTO;
import com.geihou.module.finance.api.product.dto.SkuAvailabilityRespDTO;
import com.geihou.module.finance.api.product.dto.SkuRespDTO;
import com.geihou.module.finance.api.product.dto.SpuRespDTO;

import java.util.List;
import java.util.Map;

/**
 * Product API contract used by Geihou modules (order, cart, BOM, marketing).
 *
 * <p>This is a pure Java contract in the finance API module. RPC annotations,
 * implementation, caching, and runtime behavior are intentionally out of scope.
 * The HTTP implementation is provided in geihou-module-finance-biz via
 * {@code ProductApiImpl} exposed at {@code /rpc-api/finance/product}.
 *
 * <p>Tenant context is propagated via {@code TenantContextHolder} (implicit,
 * not passed as a parameter) following the existing geihou multi-tenant pattern.
 * All DTOs include a {@code tenantId} field so consumers can verify data ownership.
 *
 * <p>Source: PRD-G1-02 Section 3.3 dubbo-api definition.
 */
public interface ProductApi {

    /**
     * Query SPU basic information (for order / cart use).
     *
     * @param spuId SPU ID
     * @return SPU DTO, or {@code null} when not found
     */
    SpuRespDTO getSpu(Long spuId);

    /**
     * Query SKU detail (including current price + status, for cart use).
     *
     * @param skuId SKU ID
     * @return SKU DTO, or {@code null} when not found
     */
    SkuRespDTO getSku(Long skuId);

    /**
     * Batch query SKUs (for list scenarios).
     *
     * @param skuIds list of SKU IDs
     * @return map of SKU ID to SKU DTO (missing IDs are omitted)
     */
    Map<Long, SkuRespDTO> batchGetSkus(List<Long> skuIds);

    /**
     * Check SKU availability for purchase (called at cart submit, especially
     * important after cross-day cutoff).
     *
     * @param skuId    SKU ID
     * @param quantity requested quantity
     * @return availability response DTO
     */
    SkuAvailabilityRespDTO checkAvailability(Long skuId, Integer quantity);

    /**
     * Expand combo into internal SKU list (for BOM backtracking + stock deduction).
     * Only expands combos with status=ACTIVE; returns empty list for PAUSED/DEPRECATED.
     *
     * @param comboSkuId combo SKU ID
     * @return list of combo item DTOs (empty if combo not found or not ACTIVE)
     */
    List<ComboItemRespDTO> expandCombo(Long comboSkuId);

    /**
     * Query addon groups + options for a given SPU (for customer-facing display).
     * Uses the product_spu_addon_group mapping table to find associated groups.
     * Only returns options with status=ACTIVE.
     *
     * @param spuId SPU ID
     * @return list of addon group DTOs (empty if none associated)
     */
    List<AddonGroupRespDTO> getAddonGroupsBySpu(Long spuId);
}
