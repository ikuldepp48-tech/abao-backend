package cn.iocoder.yudao.module.restaurant.dal.mysql.dish;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSkuPageReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSkuDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RestaurantDishSkuMapper extends BaseMapperX<RestaurantDishSkuDO> {

    default PageResult<RestaurantDishSkuDO> selectPage(RestaurantDishSkuPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RestaurantDishSkuDO>()
                .eqIfPresent(RestaurantDishSkuDO::getSpuId, reqVO.getSpuId())
                .likeIfPresent(RestaurantDishSkuDO::getName, reqVO.getName())
                .eqIfPresent(RestaurantDishSkuDO::getStatus, reqVO.getStatus())
                .orderByAsc(RestaurantDishSkuDO::getSort));
    }

    default List<RestaurantDishSkuDO> selectListBySpuId(Long spuId) {
        return selectList(new LambdaQueryWrapper<RestaurantDishSkuDO>()
                .eq(RestaurantDishSkuDO::getSpuId, spuId)
                .orderByAsc(RestaurantDishSkuDO::getSort));
    }

    default void deleteBySpuId(Long spuId) {
        delete(new LambdaQueryWrapper<RestaurantDishSkuDO>()
                .eq(RestaurantDishSkuDO::getSpuId, spuId));
    }

}
