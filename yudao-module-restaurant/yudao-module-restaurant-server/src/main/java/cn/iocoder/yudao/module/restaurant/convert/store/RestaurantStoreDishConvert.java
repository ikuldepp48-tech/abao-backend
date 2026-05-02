package cn.iocoder.yudao.module.restaurant.convert.store;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.*;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDishDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RestaurantStoreDishConvert {

    RestaurantStoreDishConvert INSTANCE = Mappers.getMapper(RestaurantStoreDishConvert.class);

    RestaurantStoreDishDO convert(RestaurantStoreDishCreateReqVO bean);

    RestaurantStoreDishDO convert(RestaurantStoreDishUpdateReqVO bean);

    RestaurantStoreDishRespVO convert(RestaurantStoreDishDO bean);

    List<RestaurantStoreDishRespVO> convertList(List<RestaurantStoreDishDO> list);

    PageResult<RestaurantStoreDishRespVO> convertPage(PageResult<RestaurantStoreDishDO> page);

}
