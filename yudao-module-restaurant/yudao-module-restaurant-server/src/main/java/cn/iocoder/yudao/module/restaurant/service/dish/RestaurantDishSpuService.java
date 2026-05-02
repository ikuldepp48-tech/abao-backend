package cn.iocoder.yudao.module.restaurant.service.dish;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSpuCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSpuPageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSpuUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO;

import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;

/**
 * 菜品 SPU Service 接口
 */
public interface RestaurantDishSpuService {

    Long createDishSpu(@Valid RestaurantDishSpuCreateReqVO createReqVO);

    void updateDishSpu(@Valid RestaurantDishSpuUpdateReqVO updateReqVO);

    void deleteDishSpu(Long id);

    RestaurantDishSpuDO getDishSpu(Long id);

    List<RestaurantDishSpuDO> getDishSpuList(Collection<Long> ids);

    PageResult<RestaurantDishSpuDO> getDishSpuPage(RestaurantDishSpuPageReqVO pageReqVO);

    List<RestaurantDishSpuDO> getDishSpuList();

}
