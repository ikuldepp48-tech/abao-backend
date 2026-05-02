package cn.iocoder.yudao.module.restaurant.service.dish;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSkuCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSkuPageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSkuUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSkuDO;

import jakarta.validation.Valid;
import java.util.List;

public interface RestaurantDishSkuService {

    Long createSku(@Valid RestaurantDishSkuCreateReqVO createReqVO);

    void updateSku(@Valid RestaurantDishSkuUpdateReqVO updateReqVO);

    void deleteSku(Long id);

    RestaurantDishSkuDO getSku(Long id);

    List<RestaurantDishSkuDO> getSkuListBySpuId(Long spuId);

    PageResult<RestaurantDishSkuDO> getSkuPage(RestaurantDishSkuPageReqVO pageReqVO);

}
