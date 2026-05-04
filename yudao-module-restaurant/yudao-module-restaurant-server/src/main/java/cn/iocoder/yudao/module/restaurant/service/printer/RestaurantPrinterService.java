package cn.iocoder.yudao.module.restaurant.service.printer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.printer.vo.RestaurantPrinterSaveReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.printer.RestaurantPrinterDO;

import java.util.List;

public interface RestaurantPrinterService {

    /** 创建打印机 */
    Long createPrinter(RestaurantPrinterSaveReqVO reqVO);

    /** 更新打印机 */
    void updatePrinter(RestaurantPrinterSaveReqVO reqVO);

    /** 删除打印机 */
    void deletePrinter(Long id);

    /** 打印机详情 */
    RestaurantPrinterDO getPrinter(Long id);

    /** 打印机分页 */
    PageResult<RestaurantPrinterDO> getPrinterPage(Integer pageNo, Integer pageSize);

    /** 获取门店的厨打打印机 */
    List<RestaurantPrinterDO> listKitchenPrinters(Long storeId);
}
