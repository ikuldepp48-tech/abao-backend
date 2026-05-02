package cn.iocoder.yudao.module.restaurant.service.table;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.table.vo.*;
import cn.iocoder.yudao.module.restaurant.controller.app.table.vo.RestaurantTableScanRespVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.table.RestaurantTableDO;

import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface RestaurantTableService {

    Long createTable(@Valid RestaurantTableCreateReqVO createReqVO);

    void updateTable(@Valid RestaurantTableUpdateReqVO updateReqVO);

    void deleteTable(Long id);

    RestaurantTableDO getTable(Long id);

    List<RestaurantTableDO> getTableList(Collection<Long> ids);

    PageResult<RestaurantTableDO> getTablePage(RestaurantTablePageReqVO pageReqVO);

    List<RestaurantTableDO> getTableList();

    /** 批量创建桌台，返回 {成功数, 跳过数} */
    Map<String, Integer> batchCreateTable(@Valid RestaurantTableBatchCreateReqVO reqVO);

    /** 生成桌台二维码，返回二维码 PNG 字节数组 */
    byte[] generateQrCode(Long id);

    /** 批量导出门店所有桌台二维码 PDF，返回 PDF 字节数组 */
    byte[] exportQrCodePdf(Long storeId);

    /** 扫码解析：根据 storeId + tableId 返回门店和桌台信息 */
    RestaurantTableScanRespVO scanTable(Long storeId, Long tableId);

}
