package cn.iocoder.yudao.module.restaurant.dal.mysql.brand;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandPageReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.brand.RestaurantBrandDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RestaurantBrandMapper extends BaseMapperX<RestaurantBrandDO> {

    default PageResult<RestaurantBrandDO> selectPage(RestaurantBrandPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RestaurantBrandDO>()
                .likeIfPresent(RestaurantBrandDO::getName, reqVO.getName())
                .eqIfPresent(RestaurantBrandDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(RestaurantBrandDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(RestaurantBrandDO::getId));
    }

    default RestaurantBrandDO selectByCode(String code) {
        return selectOne(RestaurantBrandDO::getCode, code);
    }

    default RestaurantBrandDO selectByName(String name) {
        return selectOne(RestaurantBrandDO::getName, name);
    }

}
