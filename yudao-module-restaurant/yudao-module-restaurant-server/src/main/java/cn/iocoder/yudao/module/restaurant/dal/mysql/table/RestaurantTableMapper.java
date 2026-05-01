package cn.iocoder.yudao.module.restaurant.dal.mysql.table;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.controller.admin.table.vo.RestaurantTablePageReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.table.RestaurantTableDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RestaurantTableMapper extends BaseMapperX<RestaurantTableDO> {

    default PageResult<RestaurantTableDO> selectPage(RestaurantTablePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RestaurantTableDO>()
                .eqIfPresent(RestaurantTableDO::getStoreId, reqVO.getStoreId())
                .eqIfPresent(RestaurantTableDO::getArea, reqVO.getArea())
                .likeIfPresent(RestaurantTableDO::getTableNo, reqVO.getTableNo())
                .eqIfPresent(RestaurantTableDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(RestaurantTableDO::getCreateTime, reqVO.getCreateTime())
                .orderByAsc(RestaurantTableDO::getTableNo));
    }

    default RestaurantTableDO selectByStoreIdAndTableNo(Long storeId, String tableNo) {
        return selectOne(new LambdaQueryWrapperX<RestaurantTableDO>()
                .eq(RestaurantTableDO::getStoreId, storeId)
                .eq(RestaurantTableDO::getTableNo, tableNo));
    }

}
