package cn.iocoder.yudao.module.restaurant.dal.mysql.printer;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.printer.RestaurantPrintTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RestaurantPrintTaskMapper extends BaseMapperX<RestaurantPrintTaskDO> {

    /** 查询失败且未超过最大重试次数的任务 */
    default List<RestaurantPrintTaskDO> selectFailedRetryable() {
        return selectList(new LambdaQueryWrapperX<RestaurantPrintTaskDO>()
                .eq(RestaurantPrintTaskDO::getStatus, 2)
                .lt(RestaurantPrintTaskDO::getRetryCount, 3));
    }
}
