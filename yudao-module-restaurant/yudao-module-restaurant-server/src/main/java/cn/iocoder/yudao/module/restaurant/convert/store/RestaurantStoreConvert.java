package cn.iocoder.yudao.module.restaurant.convert.store;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStoreCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStoreRespVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStoreUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RestaurantStoreConvert {

    RestaurantStoreConvert INSTANCE = Mappers.getMapper(RestaurantStoreConvert.class);

    RestaurantStoreDO convert(RestaurantStoreCreateReqVO bean);

    RestaurantStoreDO convert(RestaurantStoreUpdateReqVO bean);

    RestaurantStoreRespVO convert(RestaurantStoreDO bean);

    List<RestaurantStoreRespVO> convertList(List<RestaurantStoreDO> list);

    PageResult<RestaurantStoreRespVO> convertPage(PageResult<RestaurantStoreDO> page);

}
