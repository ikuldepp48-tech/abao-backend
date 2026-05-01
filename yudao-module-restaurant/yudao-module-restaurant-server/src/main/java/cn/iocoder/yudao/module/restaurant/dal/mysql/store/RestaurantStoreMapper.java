package cn.iocoder.yudao.module.restaurant.dal.mysql.store;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStorePageReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RestaurantStoreMapper extends BaseMapperX<RestaurantStoreDO> {

    default PageResult<RestaurantStoreDO> selectPage(RestaurantStorePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RestaurantStoreDO>()
                .likeIfPresent(RestaurantStoreDO::getName, reqVO.getName())
                .eqIfPresent(RestaurantStoreDO::getBrandId, reqVO.getBrandId())
                .eqIfPresent(RestaurantStoreDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(RestaurantStoreDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(RestaurantStoreDO::getId));
    }

    default RestaurantStoreDO selectByCode(String code) {
        return selectOne(RestaurantStoreDO::getCode, code);
    }

    default RestaurantStoreDO selectByName(String name) {
        return selectOne(RestaurantStoreDO::getName, name);
    }

}
