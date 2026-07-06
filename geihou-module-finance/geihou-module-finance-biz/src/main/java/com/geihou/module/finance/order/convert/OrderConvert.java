package com.geihou.module.finance.order.convert;

import com.geihou.module.finance.api.order.enums.OrderStatusEnum;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderItemDO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Order convert utility for mapping DOs to VOs.
 *
 * <p>Handles the conversion of OrderDO + OrderItemDO list to OrderCreateRespVO.
 * Snapshot fields from SkuRespDTO/SpuRespDTO are set directly in OrderServiceImpl
 * during creation (not via this convert).
 */
public final class OrderConvert {

    private OrderConvert() {
    }

    /**
     * Convert OrderDO to OrderCreateRespVO (without items).
     */
    public static OrderCreateRespVO convert(OrderDO order) {
        OrderCreateRespVO resp = new OrderCreateRespVO();
        resp.setOrderId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setBusinessDate(order.getBusinessDate());
        resp.setStatus(order.getStatus());
        resp.setChannel(order.getChannel());
        resp.setCreateTime(order.getCreateTime());
        return resp;
    }

    /**
     * Convert OrderDO + OrderItemDO list to OrderCreateRespVO (with items).
     */
    public static OrderCreateRespVO convert(OrderDO order, List<OrderItemDO> items) {
        OrderCreateRespVO resp = convert(order);
        List<OrderCreateRespVO.OrderItemRespVO> itemResps = items.stream()
                .map(OrderConvert::convertItem)
                .collect(Collectors.toList());
        resp.setItems(itemResps);
        return resp;
    }

    /**
     * Convert OrderItemDO to OrderItemRespVO.
     */
    public static OrderCreateRespVO.OrderItemRespVO convertItem(OrderItemDO item) {
        OrderCreateRespVO.OrderItemRespVO resp = new OrderCreateRespVO.OrderItemRespVO();
        resp.setSkuId(item.getSkuId());
        resp.setSkuName(item.getSkuName());
        resp.setUnitPrice(item.getUnitPrice());
        resp.setQuantity(item.getQuantity());
        resp.setItemTotal(item.getItemTotal());
        return resp;
    }
}
