package cn.iocoder.yudao.module.restaurant.dal.mysql.combo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.controller.admin.combo.vo.RestaurantComboPageReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.combo.RestaurantComboDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RestaurantComboMapper extends BaseMapperX<RestaurantComboDO> {

    default PageResult<RestaurantComboDO> selectPage(RestaurantComboPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RestaurantComboDO>()
                .eqIfPresent(RestaurantComboDO::getBrandId, reqVO.getBrandId())
                .likeIfPresent(RestaurantComboDO::getName, reqVO.getName())
                .eqIfPresent(RestaurantComboDO::getStatus, reqVO.getStatus())
                .orderByAsc(RestaurantComboDO::getSort));
    }

    default List<RestaurantComboDO> selectListByBrandId(Long brandId) {
        return selectList(new LambdaQueryWrapperX<RestaurantComboDO>()
                .eq(RestaurantComboDO::getBrandId, brandId)
                .orderByAsc(RestaurantComboDO::getSort));
    }

}
