package cn.iocoder.yudao.module.restaurant.service.store;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.*;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDishDO;

import jakarta.validation.Valid;
import java.util.List;

public interface RestaurantStoreDishService {

    Long createStoreDish(@Valid RestaurantStoreDishCreateReqVO createReqVO);

    void updateStoreDish(@Valid RestaurantStoreDishUpdateReqVO updateReqVO);

    void deleteStoreDish(Long id);

    RestaurantStoreDishDO getStoreDish(Long id);

    List<RestaurantStoreDishDO> getStoreDishListByStoreId(Long storeId);

    PageResult<RestaurantStoreDishDO> getStoreDishPage(RestaurantStoreDishPageReqVO pageReqVO);

    int batchSoldOut(List<Long> ids);

    int batchRestore(List<Long> ids);

    int batchUpdateStatus(List<Long> ids, Integer status);

}
