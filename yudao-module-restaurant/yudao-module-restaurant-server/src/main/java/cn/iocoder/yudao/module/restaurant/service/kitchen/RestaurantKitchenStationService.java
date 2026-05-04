package cn.iocoder.yudao.module.restaurant.service.kitchen;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.kitchen.vo.RestaurantKitchenStationSaveReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.kitchen.RestaurantKitchenStationDO;

import java.util.List;

public interface RestaurantKitchenStationService {

    /** 创建档口 */
    Long createStation(RestaurantKitchenStationSaveReqVO reqVO);

    /** 更新档口 */
    void updateStation(RestaurantKitchenStationSaveReqVO reqVO);

    /** 删除档口 */
    void deleteStation(Long id);

    /** 档口详情 */
    RestaurantKitchenStationDO getStation(Long id);

    /** 档口分页 */
    PageResult<RestaurantKitchenStationDO> getStationPage(Integer pageNo, Integer pageSize);

    /** 获取所有启用的档口 */
    List<RestaurantKitchenStationDO> listEnabledStations();
}
