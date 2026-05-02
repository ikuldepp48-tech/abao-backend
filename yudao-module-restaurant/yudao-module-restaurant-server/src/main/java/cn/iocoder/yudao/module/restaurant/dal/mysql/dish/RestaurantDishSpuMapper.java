package cn.iocoder.yudao.module.restaurant.dal.mysql.dish;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSpuPageReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RestaurantDishSpuMapper extends BaseMapperX<RestaurantDishSpuDO> {

    default PageResult<RestaurantDishSpuDO> selectPage(RestaurantDishSpuPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RestaurantDishSpuDO>()
                .eqIfPresent(RestaurantDishSpuDO::getCategoryId, reqVO.getCategoryId())
                .likeIfPresent(RestaurantDishSpuDO::getName, reqVO.getName())
                .eqIfPresent(RestaurantDishSpuDO::getStatus, reqVO.getStatus())
                .orderByAsc(RestaurantDishSpuDO::getSort));
    }

}
