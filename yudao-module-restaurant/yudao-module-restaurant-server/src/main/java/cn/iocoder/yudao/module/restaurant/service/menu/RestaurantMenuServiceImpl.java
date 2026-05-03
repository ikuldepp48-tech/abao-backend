package cn.iocoder.yudao.module.restaurant.service.menu;

import cn.iocoder.yudao.module.restaurant.controller.app.menu.vo.AppMenuRespVO;
import cn.iocoder.yudao.module.restaurant.controller.app.menu.vo.AppMenuRespVO.*;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishAddonDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishSpuAddonRelDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.category.RestaurantCategoryDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.combo.RestaurantComboDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.combo.RestaurantComboItemDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSkuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDishDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.addon.RestaurantDishAddonMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.addon.RestaurantDishSpuAddonRelMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.category.RestaurantCategoryMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.combo.RestaurantComboMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSkuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSpuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.combo.RestaurantComboItemMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreDishMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RestaurantMenuServiceImpl implements RestaurantMenuService {

    @Resource
    private RestaurantStoreMapper storeMapper;

    @Resource
    private RestaurantCategoryMapper categoryMapper;

    @Resource
    private RestaurantDishSpuMapper dishSpuMapper;

    @Resource
    private RestaurantDishSkuMapper skuMapper;

    @Resource
    private RestaurantDishAddonMapper addonMapper;

    @Resource
    private RestaurantDishSpuAddonRelMapper spuAddonRelMapper;

    @Resource
    private RestaurantStoreDishMapper storeDishMapper;

    @Resource
    private RestaurantComboMapper comboMapper;

    @Resource
    private RestaurantComboItemMapper comboItemMapper;

    @Resource
    private CacheManager cacheManager;

    private static final String MENU_CACHE_NAME = "restaurant_menu#5m";

    @Override
    public AppMenuRespVO getMenu(Long storeId) {
        Cache cache = cacheManager.getCache(MENU_CACHE_NAME);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(storeId);
            if (wrapper != null) {
                log.info("menu cache HIT for store {}", storeId);
                return (AppMenuRespVO) wrapper.get();
            }
        }
        log.info("menu cache MISS for store {}", storeId);
        AppMenuRespVO result = buildMenu(storeId);
        if (cache != null && result != null && result.getCategories() != null && !result.getCategories().isEmpty()) {
            cache.put(storeId, result);
        }
        return result;
    }

    private AppMenuRespVO buildMenu(Long storeId) {
        AppMenuRespVO result = new AppMenuRespVO();
        result.setCategories(List.of());

        RestaurantStoreDO store = storeMapper.selectById(storeId);
        if (store == null) {
            return result;
        }
        result.setStore(buildStoreInfo(store));

        List<RestaurantCategoryDO> allCategories = categoryMapper.selectList()
                .stream().filter(c -> c.getStatus() == 0).toList();

        List<RestaurantDishSpuDO> allDishes = dishSpuMapper.selectList()
                .stream().filter(d -> d.getStatus() == 0).toList();

        List<RestaurantDishSkuDO> allSkus = skuMapper.selectList()
                .stream().filter(s -> s.getStatus() == null || s.getStatus() == 0).toList();

        List<RestaurantStoreDishDO> storeDishes = storeDishMapper.selectListByStoreId(storeId);
        Map<Long, RestaurantStoreDishDO> storeDishBySpuId = storeDishes.stream()
                .collect(Collectors.toMap(RestaurantStoreDishDO::getSpuId, sd -> sd, (a, b) -> a));

        List<RestaurantDishSpuAddonRelDO> allAddonRels = spuAddonRelMapper.selectList();
        // 按门店品牌过滤加料（而非全租户）
        List<RestaurantDishAddonDO> allAddons = addonMapper.selectListByBrand(store.getBrandId())
                .stream().filter(a -> a.getStatus() == 0).toList();

        List<RestaurantComboDO> combos = comboMapper.selectList()
                .stream().filter(c -> c.getStatus() == 0).toList();

        Map<Long, List<RestaurantDishSkuDO>> skusBySpuId = allSkus.stream()
                .collect(Collectors.groupingBy(RestaurantDishSkuDO::getSpuId));
        Map<Long, List<RestaurantDishSpuAddonRelDO>> addonRelsBySpuId = allAddonRels.stream()
                .collect(Collectors.groupingBy(RestaurantDishSpuAddonRelDO::getSpuId));
        Map<Long, RestaurantDishAddonDO> addonById = allAddons.stream()
                .collect(Collectors.toMap(RestaurantDishAddonDO::getId, a -> a));

        Map<Long, List<RestaurantDishSpuDO>> dishesByCategoryId = allDishes.stream()
                .collect(Collectors.groupingBy(RestaurantDishSpuDO::getCategoryId));

        result.setCategories(buildCategoryTree(allCategories, dishesByCategoryId, skusBySpuId,
                addonRelsBySpuId, addonById, storeDishBySpuId));
        Map<Long, String> spuNameMap = allDishes.stream()
                .collect(Collectors.toMap(RestaurantDishSpuDO::getId, RestaurantDishSpuDO::getName, (a, b) -> a));
        Map<Long, String> skuNameMap = allSkus.stream()
                .collect(Collectors.toMap(RestaurantDishSkuDO::getId, RestaurantDishSkuDO::getName, (a, b) -> a));
        result.setCombos(buildComboList(combos, spuNameMap, skuNameMap));
        return result;
    }

    @Override
    public void evictCache(Long storeId) {
        log.info("menu cache EVICT for store {}", storeId);
        Cache cache = cacheManager.getCache(MENU_CACHE_NAME);
        if (cache != null) {
            cache.evict(storeId);
        }
    }

    // --- private builders ---

    private StoreInfo buildStoreInfo(RestaurantStoreDO store) {
        StoreInfo info = new StoreInfo();
        info.setId(store.getId());
        info.setName(store.getName());
        info.setBusinessHours(store.getBusinessHours());
        info.setIsOpen(store.getStatus() != null && store.getStatus() == 0);
        return info;
    }

    private List<CategoryMenu> buildCategoryTree(List<RestaurantCategoryDO> allCategories,
                                                  Map<Long, List<RestaurantDishSpuDO>> dishesByCategoryId,
                                                  Map<Long, List<RestaurantDishSkuDO>> skusBySpuId,
                                                  Map<Long, List<RestaurantDishSpuAddonRelDO>> addonRelsBySpuId,
                                                  Map<Long, RestaurantDishAddonDO> addonById,
                                                  Map<Long, RestaurantStoreDishDO> storeDishBySpuId) {
        Map<Long, CategoryMenu> nodeMap = new LinkedHashMap<>();
        for (RestaurantCategoryDO cat : allCategories) {
            CategoryMenu cm = new CategoryMenu();
            cm.setId(cat.getId());
            cm.setName(cat.getName());
            cm.setLevel(cat.getParentId() == null ? 1 : 2);
            cm.setSort(cat.getSort());
            cm.setChildren(new ArrayList<>());
            cm.setDishes(new ArrayList<>());
            nodeMap.put(cat.getId(), cm);
        }

        List<CategoryMenu> roots = new ArrayList<>();
        for (RestaurantCategoryDO cat : allCategories) {
            CategoryMenu cm = nodeMap.get(cat.getId());
            if (cat.getParentId() == null || !nodeMap.containsKey(cat.getParentId())) {
                roots.add(cm);
            } else {
                nodeMap.get(cat.getParentId()).getChildren().add(cm);
            }
        }

        for (RestaurantCategoryDO cat : allCategories) {
            CategoryMenu cm = nodeMap.get(cat.getId());
            List<RestaurantDishSpuDO> catDishes = dishesByCategoryId.getOrDefault(cat.getId(), List.of());
            for (RestaurantDishSpuDO dish : catDishes) {
                // 门店级可用性过滤：isAvailable=false 或 status=1 → 不返回
                RestaurantStoreDishDO sd = storeDishBySpuId.get(dish.getId());
                if (sd != null) {
                    if (Boolean.FALSE.equals(sd.getIsAvailable()) || Integer.valueOf(1).equals(sd.getStatus())) {
                        continue;
                    }
                }
                cm.getDishes().add(buildDishItem(dish, skusBySpuId, addonRelsBySpuId, addonById, sd));
            }
        }

        return roots;
    }

    private DishItem buildDishItem(RestaurantDishSpuDO dish,
                                    Map<Long, List<RestaurantDishSkuDO>> skusBySpuId,
                                    Map<Long, List<RestaurantDishSpuAddonRelDO>> addonRelsBySpuId,
                                    Map<Long, RestaurantDishAddonDO> addonById,
                                    RestaurantStoreDishDO sd) {
        DishItem di = new DishItem();
        di.setSpuId(dish.getId());
        di.setName(dish.getName());
        di.setCoverUrl(dish.getImage());
        di.setSubtitle(dish.getDescription());
        di.setIsSignature(dish.getIsSignature());
        di.setIsNew(dish.getIsNew());
        di.setSort(dish.getSort());

        // 构建标签
        List<String> tags = new ArrayList<>();
        if (Boolean.TRUE.equals(dish.getIsSignature())) tags.add("招牌");
        if (Boolean.TRUE.equals(dish.getIsNew())) tags.add("新品");
        di.setTags(tags.isEmpty() ? null : tags);

        List<RestaurantDishSkuDO> skus = skusBySpuId.getOrDefault(dish.getId(), List.of());
        List<SkuItem> skuItems = new ArrayList<>();
        // 门店级价格覆盖
        BigDecimal storePrice = sd != null ? sd.getPrice() : null;
        BigDecimal minP = null, maxP = null;
        for (RestaurantDishSkuDO sku : skus) {
            SkuItem si = new SkuItem();
            si.setId(sku.getId());
            si.setName(sku.getName());
            // 价格优先级：store_dish.price > sku.price
            BigDecimal finalPrice = storePrice != null ? storePrice : sku.getPrice();
            si.setPrice(finalPrice);
            si.setProperties(sku.getProperties());
            skuItems.add(si);
            if (minP == null || finalPrice.compareTo(minP) < 0) minP = finalPrice;
            if (maxP == null || finalPrice.compareTo(maxP) > 0) maxP = finalPrice;
        }
        di.setSkus(skuItems);
        // 无SKU时，用SPU默认价格兜底
        if (minP == null && dish.getPrice() != null) {
            minP = maxP = dish.getPrice();
        }
        di.setMinPrice(minP);
        di.setMaxPrice(maxP);

        di.setIsSoldOut(sd != null && Boolean.TRUE.equals(sd.getIsSoldOut()));

        List<RestaurantDishSpuAddonRelDO> rels = addonRelsBySpuId.getOrDefault(dish.getId(), List.of());
        Map<String, List<RestaurantDishAddonDO>> addonsByGroup = new LinkedHashMap<>();
        for (RestaurantDishSpuAddonRelDO rel : rels) {
            RestaurantDishAddonDO addon = addonById.get(rel.getAddonId());
            if (addon != null) {
                addonsByGroup.computeIfAbsent(addon.getGroupName(), k -> new ArrayList<>()).add(addon);
            }
        }
        List<AddonGroup> addonGroups = new ArrayList<>();
        for (Map.Entry<String, List<RestaurantDishAddonDO>> entry : addonsByGroup.entrySet()) {
            AddonGroup ag = new AddonGroup();
            ag.setGroupName(entry.getKey());
            List<RestaurantDishAddonDO> addons = entry.getValue();
            ag.setIsRequired(addons.get(0).getIsRequired());
            ag.setIsMulti(addons.get(0).getIsMulti());
            List<AddonOption> options = new ArrayList<>();
            for (RestaurantDishAddonDO a : addons) {
                AddonOption ao = new AddonOption();
                ao.setId(a.getId());
                ao.setName(a.getName());
                ao.setExtraPrice(a.getExtraPrice());
                options.add(ao);
            }
            ag.setOptions(options);
            addonGroups.add(ag);
        }
        di.setAddons(addonGroups);
        return di;
    }

    private List<ComboItem> buildComboList(List<RestaurantComboDO> combos,
                                             Map<Long, String> spuNameMap,
                                             Map<Long, String> skuNameMap) {
        List<ComboItem> result = new ArrayList<>();
        for (RestaurantComboDO c : combos) {
            ComboItem ci = new ComboItem();
            ci.setComboId(c.getId());
            ci.setName(c.getName());
            ci.setCoverUrl(c.getImage());
            ci.setPrice(c.getComboPrice());
            ci.setOriginalPrice(c.getOriginalPrice());
            ci.setDescription(c.getDescription());

            // 查询套餐包含的菜品
            List<RestaurantComboItemDO> items = comboItemMapper.selectListByComboId(c.getId());
            List<ComboItemDetail> itemDetails = new ArrayList<>();
            for (RestaurantComboItemDO item : items) {
                ComboItemDetail detail = new ComboItemDetail();
                detail.setSpuId(item.getSpuId());
                detail.setSpuName(spuNameMap.getOrDefault(item.getSpuId(), ""));
                detail.setSkuId(item.getSkuId());
                detail.setSkuName(skuNameMap.getOrDefault(item.getSkuId(), ""));
                detail.setQuantity(item.getQuantity());
                detail.setExtraPrice(item.getExtraPrice());
                itemDetails.add(detail);
            }
            ci.setItems(itemDetails);
            result.add(ci);
        }
        return result;
    }

}
