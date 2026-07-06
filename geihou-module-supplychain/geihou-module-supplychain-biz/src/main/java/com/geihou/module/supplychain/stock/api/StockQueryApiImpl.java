package com.geihou.module.supplychain.stock.api;

import com.geihou.module.supplychain.api.stock.StockQueryApi;
import com.geihou.module.supplychain.api.stock.dto.StockItemQueryRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockLocationQueryRespDTO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLocationDO;
import com.geihou.module.supplychain.stock.service.StockItemService;
import com.geihou.module.supplychain.stock.service.StockLocationService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bridge implementation of {@link StockQueryApi}.
 *
 * <p>Pure delegation — no business logic. Delegates to:
 * <ul>
 *   <li>{@link StockItemService} for getStockItemBySkuCode</li>
 *   <li>{@link StockLocationService} for getStockLocationByStoreId</li>
 * </ul>
 *
 * <p>Converts internal DOs to API DTOs. Does not expose supplychain-biz internal DOs.
 *
 * <p>Source: TASK-G2-01B2 Section 10.5
 */
@Service
public class StockQueryApiImpl implements StockQueryApi {

    private final StockItemService stockItemService;
    private final StockLocationService stockLocationService;

    public StockQueryApiImpl(StockItemService stockItemService,
                              StockLocationService stockLocationService) {
        this.stockItemService = stockItemService;
        this.stockLocationService = stockLocationService;
    }

    @Override
    public StockItemQueryRespDTO getStockItemBySkuCode(Long tenantId, String skuCode) {
        StockItemDO item = stockItemService.getBySkuCode(skuCode, tenantId);
        if (item == null) {
            return null;
        }
        return new StockItemQueryRespDTO(item.getId(), item.getSkuCode(), item.getTenantId(), item.getUnit());
    }

    @Override
    public StockLocationQueryRespDTO getStockLocationByStoreId(Long tenantId, Long storeId, String locationType) {
        // StockLocationService does not have a direct query by storeId+type.
        // Use listByTenant and filter — minimal bridge logic, no business rules.
        List<StockLocationDO> locations = stockLocationService.listByTenant(tenantId);
        for (StockLocationDO loc : locations) {
            if (storeId != null && storeId.equals(loc.getStoreId())
                    && locationType != null && locationType.equals(loc.getLocationType())
                    && Boolean.TRUE.equals(loc.getIsActive())) {
                return new StockLocationQueryRespDTO(loc.getId(), loc.getStoreId(),
                        loc.getLocationType(), loc.getTenantId());
            }
        }
        return null;
    }
}
