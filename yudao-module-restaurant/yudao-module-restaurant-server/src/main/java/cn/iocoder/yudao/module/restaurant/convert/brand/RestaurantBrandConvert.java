package cn.iocoder.yudao.module.restaurant.convert.brand;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandRespVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.brand.RestaurantBrandDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RestaurantBrandConvert {

    RestaurantBrandConvert INSTANCE = Mappers.getMapper(RestaurantBrandConvert.class);

    RestaurantBrandDO convert(RestaurantBrandCreateReqVO bean);

    RestaurantBrandDO convert(RestaurantBrandUpdateReqVO bean);

    RestaurantBrandRespVO convert(RestaurantBrandDO bean);

    List<RestaurantBrandRespVO> convertList(List<RestaurantBrandDO> list);

    PageResult<RestaurantBrandRespVO> convertPage(PageResult<RestaurantBrandDO> page);

}
