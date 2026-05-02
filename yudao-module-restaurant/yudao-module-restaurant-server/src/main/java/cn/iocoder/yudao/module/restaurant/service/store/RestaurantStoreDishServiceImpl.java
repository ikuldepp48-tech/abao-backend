package cn.iocoder.yudao.module.restaurant.service.store;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.*;
import cn.iocoder.yudao.module.restaurant.convert.store.RestaurantStoreDishConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDishDO;
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
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.STORE_DISH_NOT_AVAILABLE;

@Service
@Validated
public class RestaurantStoreDishServiceImpl implements RestaurantStoreDishService {

    @Resource
    private RestaurantStoreDishMapper storeDishMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public Long createStoreDish(RestaurantStoreDishCreateReqVO createReqVO) {
        RestaurantStoreDishDO storeDish = RestaurantStoreDishConvert.INSTANCE.convert(createReqVO);
        storeDishMapper.insert(storeDish);

        eventPublisher.publishEvent(new MenuCacheEvictEvent(Set.of(storeDish.getStoreId())));
        return storeDish.getId();
    }

    @Override
    public void updateStoreDish(RestaurantStoreDishUpdateReqVO updateReqVO) {
        validateStoreDishExists(updateReqVO.getId());
        RestaurantStoreDishDO existing = storeDishMapper.selectById(updateReqVO.getId());
        RestaurantStoreDishDO updateObj = RestaurantStoreDishConvert.INSTANCE.convert(updateReqVO);
        storeDishMapper.updateById(updateObj);

        if (existing != null) {
            eventPublisher.publishEvent(new MenuCacheEvictEvent(Set.of(existing.getStoreId())));
        }
    }

    @Override
    public void deleteStoreDish(Long id) {
        validateStoreDishExists(id);
        RestaurantStoreDishDO existing = storeDishMapper.selectById(id);
        storeDishMapper.deleteById(id);

        if (existing != null) {
            eventPublisher.publishEvent(new MenuCacheEvictEvent(Set.of(existing.getStoreId())));
        }
    }

    private void validateStoreDishExists(Long id) {
        if (storeDishMapper.selectById(id) == null) {
            throw exception(STORE_DISH_NOT_AVAILABLE);
        }
    }

    @Override
    public RestaurantStoreDishDO getStoreDish(Long id) {
        return storeDishMapper.selectById(id);
    }

    @Override
    public List<RestaurantStoreDishDO> getStoreDishListByStoreId(Long storeId) {
        return storeDishMapper.selectListByStoreId(storeId);
    }

    @Override
    public PageResult<RestaurantStoreDishDO> getStoreDishPage(RestaurantStoreDishPageReqVO pageReqVO) {
        return storeDishMapper.selectPage(pageReqVO);
    }

    @Override
    public int batchSoldOut(List<Long> ids) {
        Set<Long> storeIds = new java.util.HashSet<>();
        int count = 0;
        for (Long id : ids) {
            RestaurantStoreDishDO sd = storeDishMapper.selectById(id);
            if (sd != null) {
                sd.setIsSoldOut(true);
                storeDishMapper.updateById(sd);
                storeIds.add(sd.getStoreId());
                count++;
            }
        }
        eventPublisher.publishEvent(new MenuCacheEvictEvent(storeIds));
        return count;
    }

    @Override
    public int batchRestore(List<Long> ids) {
        Set<Long> storeIds = new java.util.HashSet<>();
        int count = 0;
        for (Long id : ids) {
            RestaurantStoreDishDO sd = storeDishMapper.selectById(id);
            if (sd != null) {
                sd.setIsSoldOut(false);
                sd.setTodaySold(0);
                storeDishMapper.updateById(sd);
                storeIds.add(sd.getStoreId());
                count++;
            }
        }
        eventPublisher.publishEvent(new MenuCacheEvictEvent(storeIds));
        return count;
    }

    @Override
    public int batchUpdateStatus(List<Long> ids, Integer status) {
        Set<Long> storeIds = new java.util.HashSet<>();
        int count = 0;
        for (Long id : ids) {
            RestaurantStoreDishDO sd = storeDishMapper.selectById(id);
            if (sd != null) {
                sd.setStatus(status);
                storeDishMapper.updateById(sd);
                storeIds.add(sd.getStoreId());
                count++;
            }
        }
        eventPublisher.publishEvent(new MenuCacheEvictEvent(storeIds));
        return count;
    }

}
