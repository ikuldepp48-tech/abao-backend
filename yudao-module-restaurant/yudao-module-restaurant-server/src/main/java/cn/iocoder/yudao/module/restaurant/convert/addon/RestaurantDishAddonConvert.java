package cn.iocoder.yudao.module.restaurant.convert.addon;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo.RestaurantDishAddonCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo.RestaurantDishAddonRespVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo.RestaurantDishAddonUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishAddonDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RestaurantDishAddonConvert {

    RestaurantDishAddonConvert INSTANCE = Mappers.getMapper(RestaurantDishAddonConvert.class);

    RestaurantDishAddonDO convert(RestaurantDishAddonCreateReqVO bean);

    RestaurantDishAddonDO convert(RestaurantDishAddonUpdateReqVO bean);

    RestaurantDishAddonRespVO convert(RestaurantDishAddonDO bean);

    List<RestaurantDishAddonRespVO> convertList(List<RestaurantDishAddonDO> list);

    PageResult<RestaurantDishAddonRespVO> convertPage(PageResult<RestaurantDishAddonDO> page);

}
