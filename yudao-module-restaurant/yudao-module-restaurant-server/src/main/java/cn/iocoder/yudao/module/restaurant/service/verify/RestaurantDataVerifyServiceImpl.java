package cn.iocoder.yudao.module.restaurant.service.verify;

import cn.iocoder.yudao.module.restaurant.dal.dataobject.category.RestaurantCategoryDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSkuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.category.RestaurantCategoryMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSkuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSpuMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;

@Service
public class RestaurantDataVerifyServiceImpl implements RestaurantDataVerifyService {

    @Resource
    private RestaurantCategoryMapper categoryMapper;

    @Resource
    private RestaurantDishSpuMapper dishSpuMapper;

    @Resource
    private RestaurantDishSkuMapper skuMapper;

    @Override
    public Map<String, Object> verifyAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orphanCategories", checkOrphanCategories());
        result.put("orphanDishes", checkOrphanDishes());
        result.put("orphanSkus", checkOrphanSkus());
        result.put("dishesWithoutSku", checkDishesWithoutSku());
        return result;
    }

    private List<Long> checkOrphanCategories() {
        List<RestaurantCategoryDO> all = categoryMapper.selectList();
        Set<Long> allIds = new HashSet<>();
        for (RestaurantCategoryDO c : all) {
            allIds.add(c.getId());
        }
        List<Long> orphans = new ArrayList<>();
        for (RestaurantCategoryDO c : all) {
            if (c.getParentId() != null && !allIds.contains(c.getParentId())) {
                orphans.add(c.getId());
            }
        }
        return orphans;
    }

    private List<Long> checkOrphanDishes() {
        List<RestaurantDishSpuDO> dishes = dishSpuMapper.selectList();
        List<RestaurantCategoryDO> categories = categoryMapper.selectList();
        Set<Long> categoryIds = new HashSet<>();
        for (RestaurantCategoryDO c : categories) {
            categoryIds.add(c.getId());
        }
        List<Long> orphans = new ArrayList<>();
        for (RestaurantDishSpuDO d : dishes) {
            if (!categoryIds.contains(d.getCategoryId())) {
                orphans.add(d.getId());
            }
        }
        return orphans;
    }

    private List<Long> checkOrphanSkus() {
        List<RestaurantDishSkuDO> skus = skuMapper.selectList();
        List<RestaurantDishSpuDO> dishes = dishSpuMapper.selectList();
        Set<Long> dishIds = new HashSet<>();
        for (RestaurantDishSpuDO d : dishes) {
            dishIds.add(d.getId());
        }
        List<Long> orphans = new ArrayList<>();
        for (RestaurantDishSkuDO s : skus) {
            if (!dishIds.contains(s.getSpuId())) {
                orphans.add(s.getId());
            }
        }
        return orphans;
    }

    private List<Long> checkDishesWithoutSku() {
        List<RestaurantDishSpuDO> dishes = dishSpuMapper.selectList();
        List<RestaurantDishSkuDO> skus = skuMapper.selectList();
        Set<Long> dishIdsWithSku = new HashSet<>();
        for (RestaurantDishSkuDO s : skus) {
            dishIdsWithSku.add(s.getSpuId());
        }
        List<Long> noSku = new ArrayList<>();
        for (RestaurantDishSpuDO d : dishes) {
            if (!dishIdsWithSku.contains(d.getId())) {
                noSku.add(d.getId());
            }
        }
        return noSku;
    }

}
