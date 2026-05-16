package cn.iocoder.yudao.module.restaurant.service.printer;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.controller.admin.printer.vo.RestaurantPrinterSaveReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.printer.RestaurantPrinterDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.printer.RestaurantPrinterMapper;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.restaurant.enums.LogRecordConstants.*;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class RestaurantPrinterServiceImpl implements RestaurantPrinterService {

    @Resource
    private RestaurantPrinterMapper printerMapper;

    @Override
    @LogRecord(type = PRINTER_TYPE, subType = PRINTER_CREATE_SUB_TYPE, bizNo = "{{#reqVO.storeId}}",
            success = PRINTER_CREATE_SUCCESS)
    public Long createPrinter(RestaurantPrinterSaveReqVO reqVO) {
        RestaurantPrinterDO printer = RestaurantPrinterDO.builder()
                .name(reqVO.getName())
                .type(reqVO.getType())
                .provider(reqVO.getProvider())
                .deviceNo(reqVO.getDeviceNo())
                .deviceKey(reqVO.getDeviceKey())
                .storeId(reqVO.getStoreId())
                .status(reqVO.getStatus() != null ? reqVO.getStatus() : 1)
                .build();
        printerMapper.insert(printer);
        return printer.getId();
    }

    @Override
    @LogRecord(type = PRINTER_TYPE, subType = PRINTER_UPDATE_SUB_TYPE, bizNo = "{{#reqVO.id}}",
            success = PRINTER_UPDATE_SUCCESS)
    public void updatePrinter(RestaurantPrinterSaveReqVO reqVO) {
        RestaurantPrinterDO printer = printerMapper.selectById(reqVO.getId());
        if (printer == null) {
            throw exception(PRINTER_NOT_EXISTS);
        }
        printer.setName(reqVO.getName());
        printer.setType(reqVO.getType());
        printer.setProvider(reqVO.getProvider());
        printer.setDeviceNo(reqVO.getDeviceNo());
        printer.setDeviceKey(reqVO.getDeviceKey());
        printer.setStoreId(reqVO.getStoreId());
        printer.setStatus(reqVO.getStatus());
        printerMapper.updateById(printer);
    }

    @Override
    @LogRecord(type = PRINTER_TYPE, subType = PRINTER_DELETE_SUB_TYPE, bizNo = "{{#id}}",
            success = PRINTER_DELETE_SUCCESS)
    public void deletePrinter(Long id) {
        printerMapper.deleteById(id);
    }

    @Override
    public RestaurantPrinterDO getPrinter(Long id) {
        return printerMapper.selectById(id);
    }

    @Override
    public PageResult<RestaurantPrinterDO> getPrinterPage(Integer pageNo, Integer pageSize) {
        return printerMapper.selectPage(new PageParam() {{
            setPageNo(pageNo);
            setPageSize(pageSize);
        }}, new LambdaQueryWrapperX<RestaurantPrinterDO>()
                .orderByAsc(RestaurantPrinterDO::getId));
    }

    @Override
    public List<RestaurantPrinterDO> listKitchenPrinters(Long storeId) {
        return printerMapper.selectList(new LambdaQueryWrapperX<RestaurantPrinterDO>()
                .eq(RestaurantPrinterDO::getType, 1)
                .eqIfPresent(RestaurantPrinterDO::getStoreId, storeId)
                .eq(RestaurantPrinterDO::getStatus, 1));
    }
}
