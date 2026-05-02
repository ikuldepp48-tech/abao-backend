package cn.iocoder.yudao.module.restaurant.convert.dish;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSpuCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSpuRespVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSpuUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RestaurantDishSpuConvert {

    RestaurantDishSpuConvert INSTANCE = Mappers.getMapper(RestaurantDishSpuConvert.class);

    RestaurantDishSpuDO convert(RestaurantDishSpuCreateReqVO bean);

    RestaurantDishSpuDO convert(RestaurantDishSpuUpdateReqVO bean);

    RestaurantDishSpuRespVO convert(RestaurantDishSpuDO bean);

    List<RestaurantDishSpuRespVO> convertList(List<RestaurantDishSpuDO> list);

    PageResult<RestaurantDishSpuRespVO> convertPage(PageResult<RestaurantDishSpuDO> page);

}
