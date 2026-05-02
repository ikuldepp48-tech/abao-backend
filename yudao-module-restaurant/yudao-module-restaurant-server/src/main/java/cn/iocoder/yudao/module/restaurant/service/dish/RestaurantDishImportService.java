package cn.iocoder.yudao.module.restaurant.service.dish;

import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishImportResultVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishImportVO;

import java.util.List;

public interface RestaurantDishImportService {

    RestaurantDishImportResultVO importDishes(List<RestaurantDishImportVO> importList);

}
