package cn.iocoder.yudao.module.restaurant.dal.mysql.store;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStoreDishPageReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDishDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RestaurantStoreDishMapper extends BaseMapperX<RestaurantStoreDishDO> {

    default PageResult<RestaurantStoreDishDO> selectPage(RestaurantStoreDishPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RestaurantStoreDishDO>()
                .eqIfPresent(RestaurantStoreDishDO::getStoreId, reqVO.getStoreId())
                .eqIfPresent(RestaurantStoreDishDO::getSpuId, reqVO.getSpuId())
                .eqIfPresent(RestaurantStoreDishDO::getStatus, reqVO.getStatus())
                .orderByAsc(RestaurantStoreDishDO::getSort));
    }

    default List<RestaurantStoreDishDO> selectListByStoreId(Long storeId) {
        return selectList(new LambdaQueryWrapperX<RestaurantStoreDishDO>()
                .eq(RestaurantStoreDishDO::getStoreId, storeId)
                .orderByAsc(RestaurantStoreDishDO::getSort));
    }

}
