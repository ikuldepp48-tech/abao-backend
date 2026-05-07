package cn.iocoder.yudao.module.restaurant.service.printer;

import cn.iocoder.yudao.module.restaurant.controller.admin.printer.template.vo.RestaurantPrinterTemplateRespVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.printer.template.vo.RestaurantPrinterTemplateSaveReqVO;

public interface RestaurantPrinterTemplateService {

    RestaurantPrinterTemplateRespVO getByPrinterId(Long printerId);

    void saveTemplate(RestaurantPrinterTemplateSaveReqVO reqVO);

}
