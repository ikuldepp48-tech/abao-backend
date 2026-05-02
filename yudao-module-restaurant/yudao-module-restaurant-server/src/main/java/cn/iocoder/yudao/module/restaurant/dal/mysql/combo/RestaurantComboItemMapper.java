package cn.iocoder.yudao.module.restaurant.dal.mysql.combo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.combo.RestaurantComboItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RestaurantComboItemMapper extends BaseMapperX<RestaurantComboItemDO> {

    default List<RestaurantComboItemDO> selectListByComboId(Long comboId) {
        return selectList(new LambdaQueryWrapperX<RestaurantComboItemDO>()
                .eq(RestaurantComboItemDO::getComboId, comboId)
                .orderByAsc(RestaurantComboItemDO::getSort));
    }

    default void deleteByComboId(Long comboId) {
        delete(new LambdaQueryWrapperX<RestaurantComboItemDO>()
                .eq(RestaurantComboItemDO::getComboId, comboId));
    }

}
