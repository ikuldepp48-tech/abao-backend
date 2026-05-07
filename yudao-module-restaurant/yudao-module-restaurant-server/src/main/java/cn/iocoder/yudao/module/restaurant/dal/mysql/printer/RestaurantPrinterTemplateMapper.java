package cn.iocoder.yudao.module.restaurant.dal.mysql.printer;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.printer.RestaurantPrinterTemplateDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RestaurantPrinterTemplateMapper extends BaseMapperX<RestaurantPrinterTemplateDO> {

    default RestaurantPrinterTemplateDO selectByPrinterId(Long printerId) {
        return selectOne(new LambdaQueryWrapperX<RestaurantPrinterTemplateDO>()
                .eq(RestaurantPrinterTemplateDO::getPrinterId, printerId));
    }

}
