package cn.iocoder.yudao.module.restaurant.dal.mysql.addon;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishSpuAddonRelDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RestaurantDishSpuAddonRelMapper extends BaseMapperX<RestaurantDishSpuAddonRelDO> {

    default List<RestaurantDishSpuAddonRelDO> selectListBySpuId(Long spuId) {
        return selectList(new LambdaQueryWrapperX<RestaurantDishSpuAddonRelDO>()
                .eq(RestaurantDishSpuAddonRelDO::getSpuId, spuId));
    }

    default void deleteBySpuId(Long spuId) {
        delete(new LambdaQueryWrapperX<RestaurantDishSpuAddonRelDO>()
                .eq(RestaurantDishSpuAddonRelDO::getSpuId, spuId));
    }

}
