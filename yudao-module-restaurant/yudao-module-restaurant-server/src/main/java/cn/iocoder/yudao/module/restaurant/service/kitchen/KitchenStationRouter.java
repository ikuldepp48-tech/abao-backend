package cn.iocoder.yudao.module.restaurant.service.kitchen;

import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.kitchen.RestaurantKitchenStationDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderItemDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSpuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderItemMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 厨房档口路由器：根据菜品分类将订单菜品分配到对应档口
 */
@Slf4j
@Component
public class KitchenStationRouter {

    @Resource
    private RestaurantOrderMapper orderMapper;

    @Resource
    private RestaurantOrderItemMapper orderItemMapper;

    @Resource
    private RestaurantDishSpuMapper dishSpuMapper;

    @Resource
    private RestaurantKitchenStationService stationService;

    /**
     * 将订单菜品路由到各档口
     *
     * @return stationId → 分配给该档口的菜品列表
     */
    public Map<Long, List<RestaurantOrderItemDO>> route(Long orderId) {
        Map<Long, List<RestaurantOrderItemDO>> result = new HashMap<>();

        // 查订单
        RestaurantOrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("[route][订单不存在 orderId({})]", orderId);
            return result;
        }

        // 查订单项
        List<RestaurantOrderItemDO> items = orderItemMapper.selectListByOrderId(orderId);
        if (items.isEmpty()) {
            return result;
        }

        // 查所有启用的档口
        List<RestaurantKitchenStationDO> stations = stationService.listEnabledStations();
        if (stations.isEmpty()) {
            log.warn("[route][没有启用的档口]");
            return result;
        }

        // 构建 categoryId → Set<stationId> 的快速查找表
        Map<Long, Set<Long>> categoryStationMap = new HashMap<>();
        for (RestaurantKitchenStationDO station : stations) {
            List<Long> categoryIds = JSONUtil.toList(station.getDishCategories(), Long.class);
            for (Long categoryId : categoryIds) {
                categoryStationMap.computeIfAbsent(categoryId, k -> new HashSet<>()).add(station.getId());
            }
        }

        // 为每个订单项查找目标档口（去重：同一菜品至少推送一次给每个档口）
        // 使用 stationItemSet 跟踪哪些 station+spu 已经分配过了
        Set<String> stationItemSet = new HashSet<>();
        for (RestaurantOrderItemDO item : items) {
            RestaurantDishSpuDO spu = dishSpuMapper.selectById(item.getSpuId());
            if (spu == null || spu.getCategoryId() == null) {
                continue;
            }
            Set<Long> stationIds = categoryStationMap.get(spu.getCategoryId());
            if (stationIds == null) {
                continue;
            }
            for (Long stationId : stationIds) {
                // 同一 station + 同一 item 只分配一次
                String key = stationId + "_" + item.getId();
                if (stationItemSet.add(key)) {
                    result.computeIfAbsent(stationId, k -> new ArrayList<>()).add(item);
                }
            }
        }

        return result;
    }
}
