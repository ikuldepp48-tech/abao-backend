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
import com.mzt.logapi.starter.annotation.LogRecord;
import org.springframework.transaction.annotation.Transactional;
import static cn.iocoder.yudao.module.restaurant.enums.LogRecordConstants.*;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.DISH_SPU_NOT_EXISTS;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.DISH_SPU_MUST_HAVE_SKU;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.DISH_SKU_PRICE_INVALID;

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
    @LogRecord(type = DISH_TYPE, subType = DISH_CREATE_SUB_TYPE, bizNo = "{{#createReqVO.name}}",
            success = DISH_CREATE_SUCCESS)
    @Transactional
    public Long createDishSpu(RestaurantDishSpuCreateReqVO createReqVO) {
        // 校验：至少1个SKU
        if (CollUtil.isEmpty(createReqVO.getSkus())) {
            throw exception(DISH_SPU_MUST_HAVE_SKU);
        }
        RestaurantDishSpuDO dish = RestaurantDishSpuConvert.INSTANCE.convert(createReqVO);
        restaurantDishSpuMapper.insert(dish);
        Long spuId = dish.getId();

        saveSkus(spuId, createReqVO.getSkus());
        calculateAndUpdatePriceRange(spuId);
        saveAddonGroupNames(spuId, createReqVO.getAddonGroupNames());
        initStoreDishes(spuId);

        Set<Long> allStoreIds = storeMapper.selectList().stream()
                .map(RestaurantStoreDO::getId).collect(Collectors.toSet());
        eventPublisher.publishEvent(new MenuCacheEvictEvent(allStoreIds));

        return spuId;
    }

    @Override
    @LogRecord(type = DISH_TYPE, subType = DISH_UPDATE_SUB_TYPE, bizNo = "{{#updateReqVO.id}}",
            success = DISH_UPDATE_SUCCESS)
    @Transactional
    public void updateDishSpu(RestaurantDishSpuUpdateReqVO updateReqVO) {
        Long spuId = updateReqVO.getId();
        validateDishSpuExists(spuId);

        // 校验：至少1个SKU
        if (CollUtil.isEmpty(updateReqVO.getSkus())) {
            throw exception(DISH_SPU_MUST_HAVE_SKU);
        }

        RestaurantDishSpuDO updateObj = RestaurantDishSpuConvert.INSTANCE.convert(updateReqVO);
        restaurantDishSpuMapper.updateById(updateObj);

        // Diff式SKU更新：有id的更新，无id的新增，不在新列表的软删除
        updateSkus(spuId, updateReqVO.getSkus());
        calculateAndUpdatePriceRange(spuId);

        spuAddonRelMapper.deleteBySpuId(spuId);
        saveAddonGroupNames(spuId, updateReqVO.getAddonGroupNames());

        eventPublisher.publishEvent(new MenuCacheEvictEvent(findStoreIdsBySpuId(spuId)));
    }

    @Override
    @LogRecord(type = DISH_TYPE, subType = DISH_DELETE_SUB_TYPE, bizNo = "{{#id}}",
            success = DISH_DELETE_SUCCESS)
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
        for (RestaurantDishSkuBaseVO skuVO : skuVOs) {
            validateSkuPrice(skuVO);
            RestaurantDishSkuDO sku = RestaurantDishSkuConvert.INSTANCE.convert(skuVO);
            sku.setSpuId(spuId);
            skuMapper.insert(sku);
        }
    }

    private void updateSkus(Long spuId, List<RestaurantDishSkuBaseVO> skuVOs) {
        // 获取现有SKU
        List<RestaurantDishSkuDO> existingSkus = skuMapper.selectListBySpuId(spuId);
        Set<Long> existingIds = existingSkus.stream()
                .map(RestaurantDishSkuDO::getId)
                .collect(Collectors.toSet());
        Set<Long> retainedIds = new HashSet<>();

        // 更新或新增
        for (RestaurantDishSkuBaseVO skuVO : skuVOs) {
            validateSkuPrice(skuVO);
            RestaurantDishSkuDO sku = RestaurantDishSkuConvert.INSTANCE.convert(skuVO);
            sku.setSpuId(spuId);
            if (skuVO.getId() != null && existingIds.contains(skuVO.getId())) {
                // 更新已有SKU
                sku.setId(skuVO.getId());
                skuMapper.updateById(sku);
                retainedIds.add(skuVO.getId());
            } else {
                // 新增SKU
                sku.setId(null);
                skuMapper.insert(sku);
                retainedIds.add(sku.getId());
            }
        }

        // 软删除不在新列表里的旧SKU（@TableLogic自动转UPDATE deleted=1）
        for (Long existingId : existingIds) {
            if (!retainedIds.contains(existingId)) {
                skuMapper.deleteById(existingId);
            }
        }
    }

    private void validateSkuPrice(RestaurantDishSkuBaseVO skuVO) {
        if (skuVO.getPrice() == null || skuVO.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw exception(DISH_SKU_PRICE_INVALID);
        }
    }

    private void calculateAndUpdatePriceRange(Long spuId) {
        List<RestaurantDishSkuDO> skus = skuMapper.selectListBySpuId(spuId);
        if (CollUtil.isEmpty(skus)) {
            return;
        }
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        for (RestaurantDishSkuDO sku : skus) {
            if (sku.getPrice() == null) {
                continue;
            }
            if (minPrice == null || sku.getPrice().compareTo(minPrice) < 0) {
                minPrice = sku.getPrice();
            }
            if (maxPrice == null || sku.getPrice().compareTo(maxPrice) > 0) {
                maxPrice = sku.getPrice();
            }
        }
        RestaurantDishSpuDO spu = new RestaurantDishSpuDO();
        spu.setId(spuId);
        spu.setMinPrice(minPrice);
        spu.setMaxPrice(maxPrice);
        restaurantDishSpuMapper.updateById(spu);
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

    @Override
    public List<String> getAddonGroupNamesBySpuId(Long spuId) {
        List<RestaurantDishSpuAddonRelDO> rels = spuAddonRelMapper.selectListBySpuId(spuId);
        if (CollUtil.isEmpty(rels)) {
            return List.of();
        }
        return rels.stream()
                .map(rel -> addonMapper.selectById(rel.getAddonId()))
                .filter(Objects::nonNull)
                .map(RestaurantDishAddonDO::getGroupName)
                .distinct()
                .collect(Collectors.toList());
    }

    private Set<Long> findStoreIdsBySpuId(Long spuId) {
        return storeDishMapper.selectList().stream()
                .filter(sd -> sd.getSpuId().equals(spuId))
                .map(RestaurantStoreDishDO::getStoreId)
                .collect(Collectors.toSet());
    }

}
