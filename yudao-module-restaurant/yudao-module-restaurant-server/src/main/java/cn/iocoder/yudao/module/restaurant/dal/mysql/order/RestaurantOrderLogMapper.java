package cn.iocoder.yudao.module.restaurant.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RestaurantOrderLogMapper extends BaseMapperX<RestaurantOrderLogDO> {

    default List<RestaurantOrderLogDO> selectListByOrderId(Long orderId) {
        return selectList(new LambdaQueryWrapperX<RestaurantOrderLogDO>()
                .eq(RestaurantOrderLogDO::getOrderId, orderId)
                .orderByAsc(RestaurantOrderLogDO::getId));
    }

}
