package cn.iocoder.yudao.module.restaurant.service.printer;

import cn.iocoder.yudao.module.restaurant.controller.admin.printer.template.vo.RestaurantPrinterTemplateSaveReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.printer.RestaurantPrinterTemplateDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.printer.RestaurantPrinterTemplateMapper;
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
 * RestaurantPrinterTemplateServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RestaurantPrinterTemplateServiceImplTest {

    @Mock
    private RestaurantPrinterTemplateMapper templateMapper;

    @InjectMocks
    private RestaurantPrinterTemplateServiceImpl templateService;

    @Test
    void saveTemplate_createPath_创建新模板成功() {
        // mock selectByPrinterId 返回 null（创建场景）
        when(templateMapper.selectByPrinterId(1L)).thenReturn(null);

        // 准备请求参数
        RestaurantPrinterTemplateSaveReqVO reqVO = new RestaurantPrinterTemplateSaveReqVO();
        reqVO.setPrinterId(1L);
        reqVO.setPaperWidth(58);
        reqVO.setHeaderText("阿堡餐饮");
        reqVO.setFooterText("谢谢光临");
        reqVO.setShowLogo(1);
        reqVO.setShowQr(0);
        reqVO.setAutoCut(1);
        reqVO.setPrintCopies(2);

        // 执行
        templateService.saveTemplate(reqVO);

        // 验证 insert 被调用，updateById 未被调用
        verify(templateMapper).selectByPrinterId(1L);
        verify(templateMapper).insert(any(RestaurantPrinterTemplateDO.class));
        verify(templateMapper, never()).updateById(any(RestaurantPrinterTemplateDO.class));
    }

    @Test
    void saveTemplate_updatePath_更新已有模板成功() {
        // 准备已存在的模板
        RestaurantPrinterTemplateDO existing = RestaurantPrinterTemplateDO.builder()
                .id(10L)
                .printerId(1L)
                .paperWidth(58)
                .headerText("旧抬头")
                .footerText("旧尾部")
                .showLogo(1)
                .showQr(0)
                .autoCut(1)
                .printCopies(1)
                .build();
        when(templateMapper.selectByPrinterId(1L)).thenReturn(existing);

        // 准备更新请求
        RestaurantPrinterTemplateSaveReqVO reqVO = new RestaurantPrinterTemplateSaveReqVO();
        reqVO.setPrinterId(1L);
        reqVO.setPaperWidth(80);
        reqVO.setHeaderText("新抬头");
        reqVO.setFooterText("新尾部");
        reqVO.setShowLogo(0);
        reqVO.setShowQr(1);
        reqVO.setAutoCut(0);
        reqVO.setPrintCopies(3);

        // 执行
        templateService.saveTemplate(reqVO);

        // 验证 updateById 被调用，insert 未被调用
        verify(templateMapper).selectByPrinterId(1L);
        verify(templateMapper).updateById(any(RestaurantPrinterTemplateDO.class));
        verify(templateMapper, never()).insert(any(RestaurantPrinterTemplateDO.class));
    }

    @Test
    void saveTemplate_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantPrinterTemplateServiceImpl.class.getMethod("saveTemplate",
                RestaurantPrinterTemplateSaveReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "saveTemplate 缺少 @LogRecord 注解");
        assertEquals(PRINTER_TEMPLATE_TYPE, annotation.type());
        assertEquals(PRINTER_TEMPLATE_SAVE_SUB_TYPE, annotation.subType());
    }

}
