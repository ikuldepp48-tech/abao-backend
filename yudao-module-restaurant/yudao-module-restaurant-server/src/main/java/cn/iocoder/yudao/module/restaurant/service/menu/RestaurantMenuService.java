package cn.iocoder.yudao.module.restaurant.service.menu;

import cn.iocoder.yudao.module.restaurant.controller.app.menu.vo.AppMenuRespVO;

public interface RestaurantMenuService {

    AppMenuRespVO getMenu(Long storeId);

    void evictCache(Long storeId);

}
