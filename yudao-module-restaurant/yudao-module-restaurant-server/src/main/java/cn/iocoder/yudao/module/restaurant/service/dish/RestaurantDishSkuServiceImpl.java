package cn.iocoder.yudao.module.restaurant.service.dish;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSkuCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSkuPageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSkuUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.convert.dish.RestaurantDishSkuConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSkuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDishDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSkuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreDishMapper;
import cn.iocoder.yudao.module.restaurant.service.menu.MenuCacheEvictEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.DISH_SKU_NOT_EXISTS;

@Service
@Validated
public class RestaurantDishSkuServiceImpl implements RestaurantDishSkuService {

    @Resource
    private RestaurantDishSkuMapper skuMapper;

    @Resource
    private RestaurantStoreDishMapper storeDishMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public Long createSku(RestaurantDishSkuCreateReqVO createReqVO) {
        RestaurantDishSkuDO sku = RestaurantDishSkuConvert.INSTANCE.convert(createReqVO);
        skuMapper.insert(sku);

        eventPublisher.publishEvent(new MenuCacheEvictEvent(findStoreIdsBySpuId(createReqVO.getSpuId())));
        return sku.getId();
    }

    @Override
    public void updateSku(RestaurantDishSkuUpdateReqVO updateReqVO) {
        validateSkuExists(updateReqVO.getId());
        RestaurantDishSkuDO updateObj = RestaurantDishSkuConvert.INSTANCE.convert(updateReqVO);
        skuMapper.updateById(updateObj);

        eventPublisher.publishEvent(new MenuCacheEvictEvent(findStoreIdsBySpuId(updateObj.getSpuId())));
    }

    @Override
    public void deleteSku(Long id) {
        validateSkuExists(id);
        RestaurantDishSkuDO sku = skuMapper.selectById(id);
        skuMapper.deleteById(id);

        if (sku != null) {
            eventPublisher.publishEvent(new MenuCacheEvictEvent(findStoreIdsBySpuId(sku.getSpuId())));
        }
    }

    private void validateSkuExists(Long id) {
        if (skuMapper.selectById(id) == null) {
            throw exception(DISH_SKU_NOT_EXISTS);
        }
    }

    @Override
    public RestaurantDishSkuDO getSku(Long id) {
        return skuMapper.selectById(id);
    }

    @Override
    public List<RestaurantDishSkuDO> getSkuListBySpuId(Long spuId) {
        return skuMapper.selectListBySpuId(spuId);
    }

    @Override
    public PageResult<RestaurantDishSkuDO> getSkuPage(RestaurantDishSkuPageReqVO pageReqVO) {
        return skuMapper.selectPage(pageReqVO);
    }

    private Set<Long> findStoreIdsBySpuId(Long spuId) {
        return storeDishMapper.selectList().stream()
                .filter(sd -> sd.getSpuId().equals(spuId))
                .map(RestaurantStoreDishDO::getStoreId)
                .collect(Collectors.toSet());
    }

}
