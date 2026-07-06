package com.geihou.module.finance.checkout.convert;

import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutSessionVO;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutSessionDO;

/**
 * Checkout convert utility for mapping DOs to VOs.
 */
public final class CheckoutConvert {

    private CheckoutConvert() {
    }

    public static CheckoutSessionVO convert(CheckoutSessionDO session) {
        CheckoutSessionVO vo = new CheckoutSessionVO();
        vo.setId(session.getId());
        vo.setSessionToken(session.getSessionToken());
        vo.setStatus(session.getStatus());
        vo.setCartId(session.getCartId());
        vo.setCustomerUserId(session.getCustomerUserId());
        vo.setShopId(session.getShopId());
        vo.setSubtotalAmount(session.getSubtotalAmount());
        vo.setDiscountAmount(session.getDiscountAmount());
        vo.setLockedDiscount(session.getLockedDiscount());
        vo.setTotalAmount(session.getTotalAmount());
        vo.setPaymentMethod(session.getPaymentMethod());
        vo.setPaymentTime(session.getPaymentTime());
        vo.setPaymentTradeNo(session.getPaymentTradeNo());
        vo.setOrderId(session.getOrderId());
        vo.setBusinessDate(session.getBusinessDate());
        vo.setExpireTime(session.getExpireTime());
        vo.setChannel(session.getChannel());
        vo.setRemark(session.getRemark());
        vo.setCreateTime(session.getCreateTime());
        vo.setUpdateTime(session.getUpdateTime());
        return vo;
    }
}
