package cn.iocoder.yudao.module.restaurant.convert.dish;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSkuBaseVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSkuCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSkuRespVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSkuUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSkuDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RestaurantDishSkuConvert {

    RestaurantDishSkuConvert INSTANCE = Mappers.getMapper(RestaurantDishSkuConvert.class);

    RestaurantDishSkuDO convert(RestaurantDishSkuBaseVO bean);

    RestaurantDishSkuDO convert(RestaurantDishSkuUpdateReqVO bean);

    RestaurantDishSkuRespVO convert(RestaurantDishSkuDO bean);

    List<RestaurantDishSkuRespVO> convertList(List<RestaurantDishSkuDO> list);

    PageResult<RestaurantDishSkuRespVO> convertPage(PageResult<RestaurantDishSkuDO> page);

}
