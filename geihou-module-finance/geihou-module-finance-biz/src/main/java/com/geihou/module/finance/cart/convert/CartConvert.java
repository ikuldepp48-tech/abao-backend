package com.geihou.module.finance.cart.convert;

import com.geihou.module.finance.cart.controller.app.customer.vo.CartItemVO;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartVO;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Cart convert utility for mapping DOs to VOs.
 *
 * <p>Handles the conversion of CartDO + CartItemDO list to CartVO.
 * Snapshot fields are preserved as-is from DOs.
 */
public final class CartConvert {

    private CartConvert() {
    }

    /**
     * Convert CartDO to CartVO (without items).
     */
    public static CartVO convert(CartDO cart) {
        CartVO vo = new CartVO();
        vo.setId(cart.getId());
        vo.setCustomerUserId(cart.getCustomerUserId());
        vo.setShopId(cart.getShopId());
        vo.setTableId(cart.getTableId());
        vo.setChannel(cart.getChannel());
        vo.setStatus(cart.getStatus());
        vo.setItemCount(cart.getItemCount());
        vo.setTotalQuantity(cart.getTotalQuantity());
        vo.setSubtotalAmount(cart.getSubtotalAmount());
        vo.setDiscountAmount(cart.getDiscountAmount());
        vo.setTotalAmount(cart.getTotalAmount());
        vo.setVersion(cart.getVersion());
        vo.setLastActivityTime(cart.getLastActivityTime());
        vo.setCreateTime(cart.getCreateTime());
        // G1-04F: map staff-assisted fields from CartDO
        vo.setIsStaffAssisted(cart.getIsStaffAssisted());
        vo.setAssistedByUserId(cart.getAssistedByUserId());
        return vo;
    }

    /**
     * Convert CartDO + CartItemDO list to CartVO (with items).
     */
    public static CartVO convert(CartDO cart, List<CartItemDO> items) {
        CartVO vo = convert(cart);
        List<CartItemVO> itemVOs = items.stream()
                .map(CartConvert::convertItem)
                .collect(Collectors.toList());
        vo.setItems(itemVOs);
        return vo;
    }

    /**
     * Convert CartItemDO to CartItemVO.
     */
    public static CartItemVO convertItem(CartItemDO item) {
        CartItemVO vo = new CartItemVO();
        vo.setId(item.getId());
        vo.setSkuId(item.getSkuId());
        vo.setSpuId(item.getSpuId());
        vo.setSkuNameSnapshot(item.getSkuNameSnapshot());
        vo.setSkuImageSnapshot(item.getSkuImageSnapshot());
        vo.setUnitPriceSnapshot(item.getUnitPriceSnapshot());
        vo.setQuantity(item.getQuantity());
        vo.setOptions(item.getOptions());
        vo.setOptionsExtraPrice(item.getOptionsExtraPrice());
        vo.setItemSubtotal(item.getItemSubtotal());
        vo.setItemDiscount(item.getItemDiscount());
        vo.setItemTotal(item.getItemTotal());
        vo.setItemState(item.getItemState());
        return vo;
    }
}
