package cn.iocoder.yudao.module.restaurant.service.brand;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandPageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.brand.RestaurantBrandDO;

import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;

/**
 * 餐饮品牌 Service 接口
 */
public interface RestaurantBrandService {

    Long createBrand(@Valid RestaurantBrandCreateReqVO createReqVO);

    void updateBrand(@Valid RestaurantBrandUpdateReqVO updateReqVO);

    void deleteBrand(Long id);

    RestaurantBrandDO getBrand(Long id);

    List<RestaurantBrandDO> getBrandList(Collection<Long> ids);

    PageResult<RestaurantBrandDO> getBrandPage(RestaurantBrandPageReqVO pageReqVO);

    List<RestaurantBrandDO> getBrandList();

}
