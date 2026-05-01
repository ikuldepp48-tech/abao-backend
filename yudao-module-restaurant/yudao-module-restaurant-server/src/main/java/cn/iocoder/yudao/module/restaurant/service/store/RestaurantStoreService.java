package cn.iocoder.yudao.module.restaurant.service.store;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStoreCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStorePageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStoreUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;

import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;

public interface RestaurantStoreService {

    Long createStore(@Valid RestaurantStoreCreateReqVO createReqVO);

    void updateStore(@Valid RestaurantStoreUpdateReqVO updateReqVO);

    void deleteStore(Long id);

    RestaurantStoreDO getStore(Long id);

    List<RestaurantStoreDO> getStoreList(Collection<Long> ids);

    PageResult<RestaurantStoreDO> getStorePage(RestaurantStorePageReqVO pageReqVO);

    List<RestaurantStoreDO> getStoreList();

}
