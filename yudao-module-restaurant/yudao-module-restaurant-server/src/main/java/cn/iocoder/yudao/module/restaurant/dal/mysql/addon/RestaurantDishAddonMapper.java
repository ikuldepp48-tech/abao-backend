package cn.iocoder.yudao.module.restaurant.dal.mysql.addon;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo.RestaurantDishAddonPageReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishAddonDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RestaurantDishAddonMapper extends BaseMapperX<RestaurantDishAddonDO> {

    default PageResult<RestaurantDishAddonDO> selectPage(RestaurantDishAddonPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RestaurantDishAddonDO>()
                .eqIfPresent(RestaurantDishAddonDO::getBrandId, reqVO.getBrandId())
                .eqIfPresent(RestaurantDishAddonDO::getGroupName, reqVO.getGroupName())
                .likeIfPresent(RestaurantDishAddonDO::getName, reqVO.getName())
                .eqIfPresent(RestaurantDishAddonDO::getStatus, reqVO.getStatus())
                .orderByAsc(RestaurantDishAddonDO::getSort));
    }

    default List<RestaurantDishAddonDO> selectListByGroupName(Long brandId, String groupName) {
        return selectList(new LambdaQueryWrapperX<RestaurantDishAddonDO>()
                .eqIfPresent(RestaurantDishAddonDO::getBrandId, brandId)
                .eq(RestaurantDishAddonDO::getGroupName, groupName)
                .orderByAsc(RestaurantDishAddonDO::getSort));
    }

    default List<String> selectDistinctGroupNames(Long brandId) {
        LambdaQueryWrapper<RestaurantDishAddonDO> wrapper = new LambdaQueryWrapper<RestaurantDishAddonDO>()
                .select(RestaurantDishAddonDO::getGroupName)
                .orderByAsc(RestaurantDishAddonDO::getSort)
                .groupBy(RestaurantDishAddonDO::getGroupName);
        if (brandId != null) {
            wrapper.eq(RestaurantDishAddonDO::getBrandId, brandId);
        }
        return selectList(wrapper)
                .stream()
                .map(RestaurantDishAddonDO::getGroupName)
                .distinct()
                .toList();
    }

    default List<RestaurantDishAddonDO> selectListByBrand(Long brandId) {
        return selectList(new LambdaQueryWrapperX<RestaurantDishAddonDO>()
                .eqIfPresent(RestaurantDishAddonDO::getBrandId, brandId)
                .eq(RestaurantDishAddonDO::getStatus, 0)
                .orderByAsc(RestaurantDishAddonDO::getGroupName)
                .orderByAsc(RestaurantDishAddonDO::getSort));
    }

}
