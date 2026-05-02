package cn.iocoder.yudao.module.restaurant.service.dish;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.*;
import cn.iocoder.yudao.module.restaurant.convert.dish.RestaurantDishSkuConvert;
import cn.iocoder.yudao.module.restaurant.convert.dish.RestaurantDishSpuConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishAddonDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishSpuAddonRelDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSkuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDishDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.addon.RestaurantDishAddonMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.addon.RestaurantDishSpuAddonRelMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSkuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSpuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreDishMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreMapper;
import cn.iocoder.yudao.module.restaurant.service.menu.MenuCacheEvictEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.DISH_SPU_NOT_EXISTS;

@Service
@Validated
public class RestaurantDishSpuServiceImpl implements RestaurantDishSpuService {

    @Resource
    private RestaurantDishSpuMapper restaurantDishSpuMapper;

    @Resource
    private RestaurantDishSkuMapper skuMapper;

    @Resource
    private RestaurantDishAddonMapper addonMapper;

    @Resource
    private RestaurantDishSpuAddonRelMapper spuAddonRelMapper;

    @Resource
    private RestaurantStoreMapper storeMapper;

    @Resource
    private RestaurantStoreDishMapper storeDishMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Long createDishSpu(RestaurantDishSpuCreateReqVO createReqVO) {
        RestaurantDishSpuDO dish = RestaurantDishSpuConvert.INSTANCE.convert(createReqVO);
        restaurantDishSpuMapper.insert(dish);
        Long spuId = dish.getId();

        saveSkus(spuId, createReqVO.getSkus());
        saveAddonGroupNames(spuId, createReqVO.getAddonGroupNames());
        initStoreDishes(spuId);

        Set<Long> allStoreIds = storeMapper.selectList().stream()
                .map(RestaurantStoreDO::getId).collect(Collectors.toSet());
        eventPublisher.publishEvent(new MenuCacheEvictEvent(allStoreIds));

        return spuId;
    }

    @Override
    @Transactional
    public void updateDishSpu(RestaurantDishSpuUpdateReqVO updateReqVO) {
        Long spuId = updateReqVO.getId();
        validateDishSpuExists(spuId);
        RestaurantDishSpuDO updateObj = RestaurantDishSpuConvert.INSTANCE.convert(updateReqVO);
        restaurantDishSpuMapper.updateById(updateObj);

        skuMapper.deleteBySpuId(spuId);
        saveSkus(spuId, updateReqVO.getSkus());

        spuAddonRelMapper.deleteBySpuId(spuId);
        saveAddonGroupNames(spuId, updateReqVO.getAddonGroupNames());

        eventPublisher.publishEvent(new MenuCacheEvictEvent(findStoreIdsBySpuId(spuId)));
    }

    @Override
    @Transactional
    public void deleteDishSpu(Long id) {
        validateDishSpuExists(id);
        skuMapper.deleteBySpuId(id);
        spuAddonRelMapper.deleteBySpuId(id);
        restaurantDishSpuMapper.deleteById(id);

        eventPublisher.publishEvent(new MenuCacheEvictEvent(findStoreIdsBySpuId(id)));
    }

    private void validateDishSpuExists(Long id) {
        if (restaurantDishSpuMapper.selectById(id) == null) {
            throw exception(DISH_SPU_NOT_EXISTS);
        }
    }

    private void saveSkus(Long spuId, List<RestaurantDishSkuBaseVO> skuVOs) {
        if (CollUtil.isEmpty(skuVOs)) {
            return;
        }
        for (RestaurantDishSkuBaseVO skuVO : skuVOs) {
            RestaurantDishSkuDO sku = RestaurantDishSkuConvert.INSTANCE.convert(skuVO);
            sku.setSpuId(spuId);
            skuMapper.insert(sku);
        }
    }

    private void saveAddonGroupNames(Long spuId, List<String> groupNames) {
        if (CollUtil.isEmpty(groupNames)) {
            return;
        }
        for (String groupName : groupNames) {
            List<RestaurantDishAddonDO> addons = addonMapper.selectListByGroupName(null, groupName);
            for (RestaurantDishAddonDO addon : addons) {
                RestaurantDishSpuAddonRelDO rel = RestaurantDishSpuAddonRelDO.builder()
                        .spuId(spuId)
                        .addonId(addon.getId())
                        .relType(1)
                        .build();
                spuAddonRelMapper.insert(rel);
            }
        }
    }

    private void initStoreDishes(Long spuId) {
        List<RestaurantStoreDO> stores = storeMapper.selectList();
        for (RestaurantStoreDO store : stores) {
            RestaurantStoreDishDO storeDish = RestaurantStoreDishDO.builder()
                    .storeId(store.getId())
                    .spuId(spuId)
                    .isAvailable(true)
                    .isSoldOut(false)
                    .todaySold(0)
                    .dailyLimit(0)
                    .status(0)
                    .build();
            storeDishMapper.insert(storeDish);
        }
    }

    @Override
    public RestaurantDishSpuDO getDishSpu(Long id) {
        return restaurantDishSpuMapper.selectById(id);
    }

    @Override
    public List<RestaurantDishSpuDO> getDishSpuList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return ListUtil.empty();
        }
        return restaurantDishSpuMapper.selectByIds(ids);
    }

    @Override
    public PageResult<RestaurantDishSpuDO> getDishSpuPage(RestaurantDishSpuPageReqVO pageReqVO) {
        return restaurantDishSpuMapper.selectPage(pageReqVO);
    }

    @Override
    public List<RestaurantDishSpuDO> getDishSpuList() {
        return restaurantDishSpuMapper.selectList();
    }

    private Set<Long> findStoreIdsBySpuId(Long spuId) {
        return storeDishMapper.selectList().stream()
                .filter(sd -> sd.getSpuId().equals(spuId))
                .map(RestaurantStoreDishDO::getStoreId)
                .collect(Collectors.toSet());
    }

}
