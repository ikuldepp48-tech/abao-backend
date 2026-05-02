package cn.iocoder.yudao.module.restaurant.dal.mysql.category;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryPageReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.category.RestaurantCategoryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RestaurantCategoryMapper extends BaseMapperX<RestaurantCategoryDO> {

    default PageResult<RestaurantCategoryDO> selectPage(RestaurantCategoryPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RestaurantCategoryDO>()
                .eqIfPresent(RestaurantCategoryDO::getParentId, reqVO.getParentId())
                .likeIfPresent(RestaurantCategoryDO::getName, reqVO.getName())
                .eqIfPresent(RestaurantCategoryDO::getStatus, reqVO.getStatus())
                .orderByAsc(RestaurantCategoryDO::getSort));
    }

    default RestaurantCategoryDO selectByName(String name) {
        return selectOne(RestaurantCategoryDO::getName, name);
    }

}
