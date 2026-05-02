package cn.iocoder.yudao.module.restaurant.service.addon;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo.RestaurantDishAddonCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo.RestaurantDishAddonPageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo.RestaurantDishAddonUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishAddonDO;

import jakarta.validation.Valid;
import java.util.List;

public interface RestaurantDishAddonService {

    Long createAddon(@Valid RestaurantDishAddonCreateReqVO createReqVO);

    void updateAddon(@Valid RestaurantDishAddonUpdateReqVO updateReqVO);

    void deleteAddon(Long id);

    RestaurantDishAddonDO getAddon(Long id);

    List<RestaurantDishAddonDO> getAddonListByGroupName(Long brandId, String groupName);

    List<String> getDistinctGroupNames(Long brandId);

    PageResult<RestaurantDishAddonDO> getAddonPage(RestaurantDishAddonPageReqVO pageReqVO);

}
