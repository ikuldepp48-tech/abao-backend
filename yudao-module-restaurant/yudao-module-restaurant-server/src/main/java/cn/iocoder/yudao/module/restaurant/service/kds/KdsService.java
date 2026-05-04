package cn.iocoder.yudao.module.restaurant.service.kds;

import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderItemDO;

import java.util.List;
import java.util.Map;

public interface KdsService {

    /** 获取档口待制作/制作中的订单菜品，按订单分组 */
    Map<Long, List<RestaurantOrderItemDO>> getStationOrderItems(Long stationId);

    /** 开始制作 */
    void startItem(Long itemId);

    /** 完成出餐 */
    void finishItem(Long itemId);
}
