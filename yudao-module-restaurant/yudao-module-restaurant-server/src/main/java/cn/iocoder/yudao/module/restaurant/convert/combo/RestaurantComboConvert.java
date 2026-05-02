package cn.iocoder.yudao.module.restaurant.convert.combo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.combo.vo.*;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.combo.RestaurantComboDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.combo.RestaurantComboItemDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RestaurantComboConvert {

    RestaurantComboConvert INSTANCE = Mappers.getMapper(RestaurantComboConvert.class);

    RestaurantComboDO convert(RestaurantComboCreateReqVO bean);

    RestaurantComboDO convert(RestaurantComboUpdateReqVO bean);

    RestaurantComboRespVO convert(RestaurantComboDO bean);

    List<RestaurantComboRespVO> convertList(List<RestaurantComboDO> list);

    PageResult<RestaurantComboRespVO> convertPage(PageResult<RestaurantComboDO> page);

    RestaurantComboItemDO convert(RestaurantComboItemBaseVO bean);

    List<RestaurantComboItemDO> convertItemList(List<RestaurantComboItemBaseVO> list);

    RestaurantComboItemBaseVO convertItem(RestaurantComboItemDO bean);

    List<RestaurantComboItemBaseVO> convertItemDOList(List<RestaurantComboItemDO> list);

}
