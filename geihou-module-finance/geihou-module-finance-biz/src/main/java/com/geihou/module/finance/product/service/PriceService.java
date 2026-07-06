package com.geihou.module.finance.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.controller.admin.vo.PriceHistoryRespVO;
import com.geihou.module.finance.product.controller.admin.vo.SkuPriceChangeReqVO;
import com.geihou.module.finance.product.dal.dataobject.ProductPriceHistoryDO;
import com.geihou.module.finance.product.dal.dataobject.ProductSkuDO;
import com.geihou.module.finance.product.dal.mapper.ProductPriceHistoryMapper;
import com.geihou.module.finance.product.dal.mapper.ProductSkuMapper;
import com.geihou.module.finance.product.enums.PriceChangeTypeEnum;
import com.geihou.module.finance.product.framework.ProductBusinessException;
import com.geihou.module.finance.product.framework.ProductErrorCodeConstants;
import com.geihou.module.finance.product.mq.ProductEventPublisher;
import com.geihou.module.finance.product.mq.event.ProductPriceChangedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Price service for SKU price changes with mandatory history logging.
 *
 * <p>Price changes update product_sku and insert into product_price_history
 * in the same transaction (Invariance 11: all price changes must leave a trace).
 * The price_history table is INSERT-only (no update/delete path).
 *
 * <p>All price fields use BigDecimal (never double/float).
 */
@Service
public class PriceService {

    private final ProductSkuMapper skuMapper;
    private final ProductPriceHistoryMapper priceHistoryMapper;
    private final ProductEventPublisher productEventPublisher;

    public PriceService(ProductSkuMapper skuMapper,
                        ProductPriceHistoryMapper priceHistoryMapper,
                        ProductEventPublisher productEventPublisher) {
        this.skuMapper = skuMapper;
        this.priceHistoryMapper = priceHistoryMapper;
        this.productEventPublisher = productEventPublisher;
    }

    /**
     * Change SKU price. Updates product_sku and writes product_price_history in the same transaction.
     *
     * @param skuId  SKU ID
     * @param reqVO  price change request (newSellingPrice, reason, changeType required)
     * @param userId user performing the change
     * @throws ProductBusinessException if SKU not found, reason missing, or selling price exceeds 110% of list
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePrice(Long skuId, SkuPriceChangeReqVO reqVO, Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // Validate reason (AC-9: reason >= 5 chars + changeType)
        if (reqVO.getReason() == null || reqVO.getReason().length() < 5) {
            throw new ProductBusinessException(ProductErrorCodeConstants.PRICE_CHANGE_REASON_REQUIRED);
        }
        Objects.requireNonNull(reqVO.getChangeType(), "changeType must not be null");
        PriceChangeTypeEnum changeType = PriceChangeTypeEnum.fromCode(reqVO.getChangeType());

        // Validate new selling price is provided
        Objects.requireNonNull(reqVO.getNewSellingPrice(), "newSellingPrice must not be null");

        ProductSkuDO sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SKU_NOT_FOUND);
        }

        // Determine new list price (may be unchanged)
        BigDecimal oldListPrice = sku.getListPrice();
        BigDecimal newListPrice = reqVO.getNewListPrice() != null ? reqVO.getNewListPrice() : oldListPrice;
        BigDecimal oldSellingPrice = sku.getSellingPrice();
        BigDecimal newSellingPrice = reqVO.getNewSellingPrice();

        // Validate selling price does not exceed 110% of new list price
        SkuService.validateSellingPriceLimit(newSellingPrice, newListPrice);

        // Update SKU prices
        sku.setListPrice(newListPrice);
        sku.setSellingPrice(newSellingPrice);
        sku.setUpdater("");
        sku.setUpdateTime(LocalDateTime.now());
        skuMapper.updateById(sku);

        // Write price history (same transaction, INSERT-only)
        LocalDateTime now = LocalDateTime.now();
        ProductPriceHistoryDO history = new ProductPriceHistoryDO();
        history.setTenantId(tenantId);
        history.setSkuId(skuId);
        history.setOldListPrice(oldListPrice);
        history.setNewListPrice(newListPrice);
        history.setOldSellingPrice(oldSellingPrice);
        history.setNewSellingPrice(newSellingPrice);
        history.setChangeReason(reqVO.getReason());
        history.setChangeType(changeType.getCode());
        history.setChangedByUserId(userId);
        history.setChangeTime(now);
        history.setCreateTime(now);
        priceHistoryMapper.insert(history);

        // G1-02G: Publish product.price.changed event via after-commit publisher.
        // Event object is constructed within the transaction for data consistency,
        // but dispatch happens afterCommit (no fire-and-forget).
        ProductPriceChangedEvent event = new ProductPriceChangedEvent();
        event.setTenantId(tenantId);
        event.setSkuId(skuId);
        event.setSpuId(sku.getSpuId());
        event.setOldSellingPrice(oldSellingPrice);
        event.setNewSellingPrice(newSellingPrice);
        event.setOldListPrice(oldListPrice);
        event.setNewListPrice(newListPrice);
        event.setChangeType(changeType.getCode());
        event.setChangeReason(reqVO.getReason());
        event.setChangedByUserId(userId);
        event.setChangeTime(now);
        productEventPublisher.publishPriceChanged(event);
    }

    /**
     * Get price history for a SKU.
     */
    public List<PriceHistoryRespVO> getPriceHistory(Long skuId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        List<ProductPriceHistoryDO> histories = priceHistoryMapper.selectList(
                new LambdaQueryWrapper<ProductPriceHistoryDO>()
                        .eq(ProductPriceHistoryDO::getSkuId, skuId)
                        .orderByDesc(ProductPriceHistoryDO::getChangeTime));

        return histories.stream()
                .map(this::toRespVO)
                .collect(Collectors.toList());
    }

    private PriceHistoryRespVO toRespVO(ProductPriceHistoryDO history) {
        PriceHistoryRespVO vo = new PriceHistoryRespVO();
        vo.setId(history.getId());
        vo.setSkuId(history.getSkuId());
        vo.setOldListPrice(history.getOldListPrice());
        vo.setNewListPrice(history.getNewListPrice());
        vo.setOldSellingPrice(history.getOldSellingPrice());
        vo.setNewSellingPrice(history.getNewSellingPrice());
        vo.setChangeReason(history.getChangeReason());
        vo.setChangeType(history.getChangeType());
        vo.setChangedByUserId(history.getChangedByUserId());
        vo.setChangeTime(history.getChangeTime());
        return vo;
    }
}
