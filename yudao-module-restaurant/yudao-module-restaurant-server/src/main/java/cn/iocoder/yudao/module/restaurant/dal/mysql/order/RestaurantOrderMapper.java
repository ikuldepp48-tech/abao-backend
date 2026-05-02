package cn.iocoder.yudao.module.restaurant.dal.mysql.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.controller.admin.order.vo.RestaurantOrderPageReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RestaurantOrderMapper extends BaseMapperX<RestaurantOrderDO> {

    default PageResult<RestaurantOrderDO> selectPage(RestaurantOrderPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RestaurantOrderDO>()
                .eqIfPresent(RestaurantOrderDO::getStoreId, reqVO.getStoreId())
                .eqIfPresent(RestaurantOrderDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(RestaurantOrderDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(RestaurantOrderDO::getId));
    }

    default RestaurantOrderDO selectByOrderNo(String orderNo) {
        return selectOne(RestaurantOrderDO::getOrderNo, orderNo);
    }

}
