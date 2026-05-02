package cn.iocoder.yudao.module.restaurant.service.category;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryPageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.convert.category.RestaurantCategoryConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.category.RestaurantCategoryDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.category.RestaurantCategoryMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreMapper;
import cn.iocoder.yudao.module.restaurant.service.menu.MenuCacheEvictEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.*;

@Service
@Validated
public class RestaurantCategoryServiceImpl implements RestaurantCategoryService {

    @Resource
    private RestaurantCategoryMapper restaurantCategoryMapper;

    @Resource
    private cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSpuMapper dishSpuMapper;

    @Resource
    private RestaurantStoreMapper storeMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public Long createCategory(RestaurantCategoryCreateReqVO createReqVO) {
        RestaurantCategoryDO category = RestaurantCategoryConvert.INSTANCE.convert(createReqVO);
        restaurantCategoryMapper.insert(category);
        return category.getId();
    }

    @Override
    public void updateCategory(RestaurantCategoryUpdateReqVO updateReqVO) {
        validateCategoryExists(updateReqVO.getId());
        RestaurantCategoryDO updateObj = RestaurantCategoryConvert.INSTANCE.convert(updateReqVO);
        restaurantCategoryMapper.updateById(updateObj);

        Set<Long> allStoreIds = storeMapper.selectList().stream()
                .map(RestaurantStoreDO::getId).collect(Collectors.toSet());
        eventPublisher.publishEvent(new MenuCacheEvictEvent(allStoreIds));
    }

    @Override
    public void deleteCategory(Long id) {
        validateCategoryExists(id);
        // 检查是否存在子分类
        if (restaurantCategoryMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RestaurantCategoryDO>()
                        .eq(RestaurantCategoryDO::getParentId, id)) > 0) {
            throw exception(CATEGORY_HAS_CHILDREN);
        }
        // 检查是否存在菜品
        if (dishSpuMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO>()
                        .eq(cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO::getCategoryId, id)) > 0) {
            throw exception(CATEGORY_HAS_DISHES);
        }
        restaurantCategoryMapper.deleteById(id);
    }

    private void validateCategoryExists(Long id) {
        if (restaurantCategoryMapper.selectById(id) == null) {
            throw exception(CATEGORY_NOT_EXISTS);
        }
    }

    @Override
    public RestaurantCategoryDO getCategory(Long id) {
        return restaurantCategoryMapper.selectById(id);
    }

    @Override
    public List<RestaurantCategoryDO> getCategoryList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return ListUtil.empty();
        }
        return restaurantCategoryMapper.selectByIds(ids);
    }

    @Override
    public PageResult<RestaurantCategoryDO> getCategoryPage(RestaurantCategoryPageReqVO pageReqVO) {
        return restaurantCategoryMapper.selectPage(pageReqVO);
    }

    @Override
    public List<RestaurantCategoryDO> getCategoryList() {
        return restaurantCategoryMapper.selectList();
    }

}
