package com.geihou.module.supplychain.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.module.supplychain.api.stock.dto.StockHealthRespDTO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Read-only implementation of stock health summary.
 *
 * <p>Source: TASK-G2-02G.
 */
@Service
public class StockHealthServiceImpl implements StockHealthService {

    private static final int RECENT_EVENT_DAYS = 7;

    private final StockBalanceMapper stockBalanceMapper;
    private final StockEventMapper stockEventMapper;

    public StockHealthServiceImpl(StockBalanceMapper stockBalanceMapper,
                                  StockEventMapper stockEventMapper) {
        this.stockBalanceMapper = stockBalanceMapper;
        this.stockEventMapper = stockEventMapper;
    }

    @Override
    public StockHealthRespDTO getStockHealth(Long tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        StockHealthRespDTO resp = new StockHealthRespDTO();
        resp.setTenantId(tenantId);
        List<StockBalanceDO> balances = stockBalanceMapper.selectList(
                new LambdaQueryWrapper<StockBalanceDO>()
                        .eq(StockBalanceDO::getTenantId, tenantId)
                        .eq(StockBalanceDO::getDeleted, false));
        resp.setLowStockItemCount((int) balances.stream()
                .filter(balance -> balance.getMinThreshold() != null)
                .filter(balance -> defaultZero(balance.getAvailableQty())
                        .compareTo(balance.getMinThreshold()) <= 0)
                .count());
        resp.setNegativeStockItemCount((int) balances.stream()
                .filter(balance -> defaultZero(balance.getAvailableQty())
                        .compareTo(BigDecimal.ZERO) < 0)
                .count());
        resp.setReservedStockItemCount((int) balances.stream()
                .filter(balance -> defaultZero(balance.getReservedQty())
                        .compareTo(BigDecimal.ZERO) > 0)
                .count());
        resp.setRecentEventCount(toInt(stockEventMapper.selectCount(
                new LambdaQueryWrapper<StockEventDO>()
                        .eq(StockEventDO::getTenantId, tenantId)
                        .ge(StockEventDO::getEventTime, LocalDateTime.now().minusDays(RECENT_EVENT_DAYS)))));

        StockEventDO latestEvent = stockEventMapper.selectOne(
                new LambdaQueryWrapper<StockEventDO>()
                        .eq(StockEventDO::getTenantId, tenantId)
                        .orderByDesc(StockEventDO::getEventTime)
                        .last("LIMIT 1"));
        resp.setLatestEventTime(latestEvent == null ? null : latestEvent.getEventTime());
        return resp;
    }

    private int toInt(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
