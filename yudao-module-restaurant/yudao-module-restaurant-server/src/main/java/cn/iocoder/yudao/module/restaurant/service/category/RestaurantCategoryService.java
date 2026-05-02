package cn.iocoder.yudao.module.restaurant.service.category;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryPageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.category.RestaurantCategoryDO;

import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;

/**
 * 菜品分类 Service 接口
 */
public interface RestaurantCategoryService {

    Long createCategory(@Valid RestaurantCategoryCreateReqVO createReqVO);

    void updateCategory(@Valid RestaurantCategoryUpdateReqVO updateReqVO);

    void deleteCategory(Long id);

    RestaurantCategoryDO getCategory(Long id);

    List<RestaurantCategoryDO> getCategoryList(Collection<Long> ids);

    PageResult<RestaurantCategoryDO> getCategoryPage(RestaurantCategoryPageReqVO pageReqVO);

    List<RestaurantCategoryDO> getCategoryList();

}
