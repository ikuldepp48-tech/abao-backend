package cn.iocoder.yudao.module.restaurant.service.dashboard;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.restaurant.controller.admin.dashboard.vo.DashboardRespVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dashboard.vo.DashboardRespVO.*;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.brand.RestaurantBrandDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderItemDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.brand.RestaurantBrandMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderItemMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardServiceImpl implements DashboardService {

    @Resource
    private RestaurantBrandMapper brandMapper;

    @Resource
    private RestaurantStoreMapper storeMapper;

    @Resource
    private RestaurantOrderMapper orderMapper;

    @Resource
    private RestaurantOrderItemMapper orderItemMapper;

    @Override
    public DashboardRespVO getDashboard() {
        DashboardRespVO vo = new DashboardRespVO();
        vo.setBrandStores(buildBrandStores());
        vo.setSalesSummary(buildSalesSummary());
        vo.setDishRanking(buildDishRanking());
        vo.setStoreRanking(buildStoreRanking());
        vo.setHotWords(buildHotWords());
        return vo;
    }

    // ========== 1. 品牌门店资料 ==========

    private List<BrandStoreVO> buildBrandStores() {
        List<RestaurantBrandDO> brands = brandMapper.selectList();
        if (CollUtil.isEmpty(brands)) return Collections.emptyList();

        List<RestaurantStoreDO> stores = storeMapper.selectList();
        Map<Long, List<RestaurantStoreDO>> brandStoreMap = stores.stream()
                .collect(Collectors.groupingBy(RestaurantStoreDO::getBrandId));

        List<BrandStoreVO> result = new ArrayList<>();
        for (RestaurantBrandDO brand : brands) {
            List<RestaurantStoreDO> brandStores = brandStoreMap.getOrDefault(brand.getId(), Collections.emptyList());
            for (RestaurantStoreDO store : brandStores) {
                BrandStoreVO item = new BrandStoreVO();
                item.setBrandId(brand.getId());
                item.setBrandName(brand.getName());
                item.setCategory(brand.getCategory());
                item.setStoreId(store.getId());
                item.setStoreName(store.getName());
                item.setStoreCode(store.getCode());
                item.setProvince(store.getProvince());
                item.setCity(store.getCity());
                item.setDistrict(store.getDistrict());
                item.setAddress(store.getAddress());
                item.setPhone(store.getPhone());
                item.setBusinessHours(store.getBusinessHours());
                item.setAreaSize(store.getAreaSize());
                item.setSeatCount(store.getSeatCount());
                item.setOpenDate(store.getOpenDate() != null ? store.getOpenDate().toString() : null);
                item.setSupportDineIn(store.getSupportDineIn());
                item.setSupportTakeout(store.getSupportTakeout());
                item.setSupportPickup(store.getSupportPickup());
                result.add(item);
            }
        }
        return result;
    }

    // ========== 2. 销售汇总 ==========

    private SalesSummaryVO buildSalesSummary() {
        SalesSummaryVO vo = new SalesSummaryVO();

        // 今日
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        List<RestaurantOrderDO> todayOrders = orderMapper.selectList(
                new LambdaQueryWrapper<RestaurantOrderDO>()
                        .between(RestaurantOrderDO::getCreateTime, todayStart, todayEnd)
                        .in(RestaurantOrderDO::getStatus, 1, 3, 4)); // 已支付/已完成
        vo.setTodayOrderCount((long) todayOrders.size());
        vo.setTodaySales(todayOrders.stream()
                .map(RestaurantOrderDO::getPayAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // 本月
        LocalDateTime monthStart = LocalDateTime.of(LocalDate.now().withDayOfMonth(1), LocalTime.MIN);
        List<RestaurantOrderDO> monthOrders = orderMapper.selectList(
                new LambdaQueryWrapper<RestaurantOrderDO>()
                        .ge(RestaurantOrderDO::getCreateTime, monthStart)
                        .in(RestaurantOrderDO::getStatus, 1, 3, 4));
        vo.setMonthOrderCount((long) monthOrders.size());
        vo.setMonthSales(monthOrders.stream()
                .map(RestaurantOrderDO::getPayAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // 门店统计
        List<RestaurantStoreDO> stores = storeMapper.selectList();
        vo.setTotalStores((long) stores.size());
        vo.setActiveStores(stores.stream().filter(s -> s.getStatus() != null && s.getStatus() == 0).count());

        return vo;
    }

    // ========== 3. 菜品销售排行 ==========

    private List<DishRankItemVO> buildDishRanking() {
        // 查询所有有效订单
        List<RestaurantOrderDO> validOrders = orderMapper.selectList(
                new LambdaQueryWrapper<RestaurantOrderDO>()
                        .in(RestaurantOrderDO::getStatus, 1, 3, 4));
        if (CollUtil.isEmpty(validOrders)) return Collections.emptyList();

        List<Long> orderIds = validOrders.stream().map(RestaurantOrderDO::getId).collect(Collectors.toList());
        Map<Long, String> orderStoreMap = validOrders.stream()
                .collect(Collectors.toMap(RestaurantOrderDO::getId, o -> String.valueOf(o.getStoreId())));

        // 构建 storeId -> storeName 映射
        List<RestaurantStoreDO> stores = storeMapper.selectList();
        Map<Long, String> storeNameMap = stores.stream()
                .collect(Collectors.toMap(RestaurantStoreDO::getId, RestaurantStoreDO::getName, (a, b) -> a));

        // 按 spuName 聚合
        List<RestaurantOrderItemDO> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<RestaurantOrderItemDO>()
                        .in(RestaurantOrderItemDO::getOrderId, orderIds));

        Map<String, DishRankItemVO> agg = new LinkedHashMap<>();
        for (RestaurantOrderItemDO item : items) {
            String key = item.getSpuName();
            DishRankItemVO rank = agg.computeIfAbsent(key, k -> {
                DishRankItemVO v = new DishRankItemVO();
                v.setDishName(k);
                v.setQuantity(0L);
                v.setAmount(BigDecimal.ZERO);
                return v;
            });
            rank.setQuantity(rank.getQuantity() + item.getQuantity());
            rank.setAmount(rank.getAmount().add(item.getSubtotal()));
            // 关联门店
            if (rank.getStoreName() == null) {
                String sidStr = orderStoreMap.get(item.getOrderId());
                if (sidStr != null) {
                    try {
                        Long sid = Long.valueOf(sidStr);
                        rank.setStoreId(sid);
                        rank.setStoreName(storeNameMap.getOrDefault(sid, "门店 #" + sid));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        return agg.values().stream()
                .sorted((a, b) -> Long.compare(b.getQuantity(), a.getQuantity()))
                .limit(10)
                .collect(Collectors.toList());
    }

    // ========== 4. 门店销售排行 ==========

    private List<StoreRankItemVO> buildStoreRanking() {
        List<RestaurantOrderDO> validOrders = orderMapper.selectList(
                new LambdaQueryWrapper<RestaurantOrderDO>()
                        .in(RestaurantOrderDO::getStatus, 1, 3, 4));
        if (CollUtil.isEmpty(validOrders)) return Collections.emptyList();

        List<RestaurantStoreDO> stores = storeMapper.selectList();
        Map<Long, String> storeNameMap = stores.stream()
                .collect(Collectors.toMap(RestaurantStoreDO::getId, RestaurantStoreDO::getName, (a, b) -> a));

        Map<Long, StoreRankItemVO> agg = new LinkedHashMap<>();
        for (RestaurantOrderDO order : validOrders) {
            Long storeId = order.getStoreId();
            StoreRankItemVO rank = agg.computeIfAbsent(storeId, k -> {
                StoreRankItemVO v = new StoreRankItemVO();
                v.setStoreId(k);
                v.setStoreName(storeNameMap.getOrDefault(k, "门店 #" + k));
                v.setOrderCount(0L);
                v.setTotalSales(BigDecimal.ZERO);
                return v;
            });
            rank.setOrderCount(rank.getOrderCount() + 1);
            rank.setTotalSales(rank.getTotalSales().add(order.getPayAmount()));
        }

        return agg.values().stream()
                .sorted((a, b) -> b.getTotalSales().compareTo(a.getTotalSales()))
                .limit(10)
                .collect(Collectors.toList());
    }

    // ========== 5. 顾客反馈热门词 ==========

    private List<HotWordVO> buildHotWords() {
        // 基于真实订单菜品数据 + 模拟反馈词，生成热门词云
        List<RestaurantOrderDO> validOrders = orderMapper.selectList(
                new LambdaQueryWrapper<RestaurantOrderDO>()
                        .in(RestaurantOrderDO::getStatus, 1, 3, 4));
        if (CollUtil.isEmpty(validOrders)) return buildSimulatedHotWords();

        List<Long> orderIds = validOrders.stream().map(RestaurantOrderDO::getId).collect(Collectors.toList());
        List<RestaurantOrderItemDO> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<RestaurantOrderItemDO>()
                        .in(RestaurantOrderItemDO::getOrderId, orderIds));

        List<HotWordVO> hotWords = new ArrayList<>();

        // 菜品名作为热词（卖得多 = 热度高）
        Map<String, Long> dishCount = items.stream()
                .collect(Collectors.groupingBy(RestaurantOrderItemDO::getSpuName, Collectors.counting()));
        for (Map.Entry<String, Long> e : dishCount.entrySet()) {
            HotWordVO word = new HotWordVO();
            word.setWord(e.getKey());
            word.setHeat(e.getValue().intValue() * 10);
            word.setSourceType("菜品");
            hotWords.add(word);
        }

        // 模拟正反馈词（基于菜品名衍生）
        String[][] positiveWords = {
                {"好吃", "口感好", "味道赞", "很香", "够味"},
                {"分量足", "实惠", "性价比高", "管饱", "超值"},
                {"出餐快", "不用等", "效率高", "准时", "速度赞"},
                {"干净", "卫生", "环境好", "整洁", "放心"},
                {"服务好", "热情", "周到", "态度好", "贴心"}
        };
        Random rng = new Random(42);
        for (String[] group : positiveWords) {
            for (String w : group) {
                HotWordVO word = new HotWordVO();
                word.setWord(w);
                word.setHeat(10 + rng.nextInt(90));
                word.setSourceType("评价");
                hotWords.add(word);
            }
        }

        return hotWords;
    }

    private List<HotWordVO> buildSimulatedHotWords() {
        // 无订单数据时的占位热词
        List<HotWordVO> list = new ArrayList<>();
        String[] words = {"好吃", "分量足", "出餐快", "干净卫生", "服务好", "性价比高", "味道赞", "环境好"};
        for (int i = 0; i < words.length; i++) {
            HotWordVO w = new HotWordVO();
            w.setWord(words[i]);
            w.setHeat(80 - i * 8);
            w.setSourceType("评价");
            list.add(w);
        }
        return list;
    }
}
