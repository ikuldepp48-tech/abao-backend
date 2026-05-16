package cn.iocoder.yudao.module.restaurant.service.printer;

import cn.iocoder.yudao.module.restaurant.controller.admin.printer.vo.RestaurantPrinterSaveReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.printer.RestaurantPrinterDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.printer.RestaurantPrinterMapper;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static cn.iocoder.yudao.module.restaurant.enums.LogRecordConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RestaurantPrinterServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RestaurantPrinterServiceImplTest {

    @Mock
    private RestaurantPrinterMapper printerMapper;

    @InjectMocks
    private RestaurantPrinterServiceImpl printerService;

    @Test
    void createPrinter_正常路径_创建打印机成功() {
        // 准备请求参数
        RestaurantPrinterSaveReqVO reqVO = new RestaurantPrinterSaveReqVO();
        reqVO.setName("厨房打印机");
        reqVO.setType(1);
        reqVO.setProvider("feieyun");
        reqVO.setDeviceNo("DEV001");
        reqVO.setDeviceKey("KEY123");
        reqVO.setStoreId(1L);
        reqVO.setStatus(1);

        // 模拟 insert 自动生成 ID
        doAnswer(invocation -> {
            RestaurantPrinterDO arg = invocation.getArgument(0);
            arg.setId(100L);
            return null;
        }).when(printerMapper).insert(any(RestaurantPrinterDO.class));

        // 执行
        Long id = printerService.createPrinter(reqVO);

        // 验证
        assertEquals(100L, id);
        verify(printerMapper).insert(any(RestaurantPrinterDO.class));
    }

    @Test
    void createPrinter_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantPrinterServiceImpl.class.getMethod("createPrinter", RestaurantPrinterSaveReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "createPrinter 缺少 @LogRecord 注解");
        assertEquals(PRINTER_TYPE, annotation.type());
        assertEquals(PRINTER_CREATE_SUB_TYPE, annotation.subType());
    }

    @Test
    void updatePrinter_正常路径_更新打印机成功() {
        // 准备已存在的打印机
        RestaurantPrinterDO existing = RestaurantPrinterDO.builder()
                .id(1L)
                .name("旧名称")
                .type(1)
                .provider("mock")
                .deviceNo("OLD001")
                .deviceKey("OLDKEY")
                .storeId(1L)
                .status(1)
                .build();
        when(printerMapper.selectById(1L)).thenReturn(existing);

        // 准备更新请求
        RestaurantPrinterSaveReqVO reqVO = new RestaurantPrinterSaveReqVO();
        reqVO.setId(1L);
        reqVO.setName("新名称");
        reqVO.setType(2);
        reqVO.setProvider("feieyun");
        reqVO.setDeviceNo("NEW001");
        reqVO.setDeviceKey("NEWKEY");
        reqVO.setStoreId(2L);
        reqVO.setStatus(0);

        // 执行
        printerService.updatePrinter(reqVO);

        // 验证
        verify(printerMapper).selectById(1L);
        verify(printerMapper).updateById(existing);
        assertEquals("新名称", existing.getName());
        assertEquals(2, existing.getType());
    }

    @Test
    void updatePrinter_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantPrinterServiceImpl.class.getMethod("updatePrinter", RestaurantPrinterSaveReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "updatePrinter 缺少 @LogRecord 注解");
        assertEquals(PRINTER_TYPE, annotation.type());
        assertEquals(PRINTER_UPDATE_SUB_TYPE, annotation.subType());
    }

    @Test
    void deletePrinter_正常路径_删除打印机成功() {
        // 执行
        printerService.deletePrinter(1L);

        // 验证
        verify(printerMapper).deleteById(1L);
    }

    @Test
    void deletePrinter_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantPrinterServiceImpl.class.getMethod("deletePrinter", Long.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "deletePrinter 缺少 @LogRecord 注解");
        assertEquals(PRINTER_TYPE, annotation.type());
        assertEquals(PRINTER_DELETE_SUB_TYPE, annotation.subType());
    }

}
