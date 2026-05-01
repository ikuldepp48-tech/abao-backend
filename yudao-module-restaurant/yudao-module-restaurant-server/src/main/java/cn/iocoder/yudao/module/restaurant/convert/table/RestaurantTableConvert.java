package cn.iocoder.yudao.module.restaurant.convert.table;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.table.vo.RestaurantTableCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.table.vo.RestaurantTableRespVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.table.vo.RestaurantTableUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.table.RestaurantTableDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RestaurantTableConvert {

    RestaurantTableConvert INSTANCE = Mappers.getMapper(RestaurantTableConvert.class);

    RestaurantTableDO convert(RestaurantTableCreateReqVO bean);

    RestaurantTableDO convert(RestaurantTableUpdateReqVO bean);

    RestaurantTableRespVO convert(RestaurantTableDO bean);

    List<RestaurantTableRespVO> convertList(List<RestaurantTableDO> list);

    PageResult<RestaurantTableRespVO> convertPage(PageResult<RestaurantTableDO> page);

}
