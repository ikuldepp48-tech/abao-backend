package cn.iocoder.yudao.module.restaurant.convert.category;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryRespVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.category.RestaurantCategoryDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RestaurantCategoryConvert {

    RestaurantCategoryConvert INSTANCE = Mappers.getMapper(RestaurantCategoryConvert.class);

    RestaurantCategoryDO convert(RestaurantCategoryCreateReqVO bean);

    RestaurantCategoryDO convert(RestaurantCategoryUpdateReqVO bean);

    RestaurantCategoryRespVO convert(RestaurantCategoryDO bean);

    List<RestaurantCategoryRespVO> convertList(List<RestaurantCategoryDO> list);

    PageResult<RestaurantCategoryRespVO> convertPage(PageResult<RestaurantCategoryDO> page);

}
