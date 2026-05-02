package cn.iocoder.yudao.module.restaurant.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RestaurantOrderItemMapper extends BaseMapperX<RestaurantOrderItemDO> {

    default List<RestaurantOrderItemDO> selectListByOrderId(Long orderId) {
        return selectList(new LambdaQueryWrapperX<RestaurantOrderItemDO>()
                .eq(RestaurantOrderItemDO::getOrderId, orderId));
    }

}
