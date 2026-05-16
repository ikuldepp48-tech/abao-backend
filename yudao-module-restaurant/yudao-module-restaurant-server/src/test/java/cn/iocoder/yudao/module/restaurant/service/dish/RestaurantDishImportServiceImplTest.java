package cn.iocoder.yudao.module.restaurant.service.dish;

import cn.idev.excel.ExcelWriter;
import cn.idev.excel.FastExcelFactory;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishImportResultVO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.addon.RestaurantDishAddonMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.category.RestaurantCategoryMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSpuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreMapper;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Collections;

import static cn.iocoder.yudao.module.restaurant.enums.LogRecordConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RestaurantDishImportServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RestaurantDishImportServiceImplTest {

    @Mock
    private RestaurantCategoryMapper categoryMapper;

    @Mock
    private RestaurantDishSpuMapper dishSpuMapper;

    @Mock
    private RestaurantDishAddonMapper addonMapper;

    @Mock
    private RestaurantStoreMapper storeMapper;

    @Mock
    private RestaurantDishImportRowExecutor rowExecutor;

    @InjectMocks
    private RestaurantDishImportServiceImpl importService;

    private byte[] emptyExcelBytes;

    @BeforeEach
    void setUp() throws Exception {
        // 创建一个空的 4-sheet Excel 文件
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ExcelWriter writer = FastExcelFactory.write(baos).build();
        writer.write(Collections.emptyList(), FastExcelFactory.writerSheet(0, "分类").build());
        writer.write(Collections.emptyList(), FastExcelFactory.writerSheet(1, "简单菜品").build());
        writer.write(Collections.emptyList(), FastExcelFactory.writerSheet(2, "多SKU菜品").build());
        writer.write(Collections.emptyList(), FastExcelFactory.writerSheet(3, "加料关联").build());
        writer.finish();
        emptyExcelBytes = baos.toByteArray();
    }

    @Test
    void importDishes_normal_空文件导入成功() throws Exception {
        // 准备 Mock 数据：所有查询都返回空列表
        when(categoryMapper.selectList()).thenReturn(Collections.emptyList());
        when(storeMapper.selectList()).thenReturn(Collections.emptyList());
        when(addonMapper.selectListByBrand(null)).thenReturn(Collections.emptyList());
        when(dishSpuMapper.selectList()).thenReturn(Collections.emptyList());

        // 创建 MockMultipartFile
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                emptyExcelBytes);

        // 执行
        RestaurantDishImportResultVO result = importService.importDishes(file);

        // 验证返回结果不为空且统计为 0
        assertNotNull(result);
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailCount());

        // 验证各 mapper 被调用（buildContext 内部调用）
        verify(categoryMapper, atLeastOnce()).selectList();
        verify(storeMapper).selectList();
        verify(addonMapper).selectListByBrand(null);
        verify(dishSpuMapper).selectList();
    }

    @Test
    void importDishes_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantDishImportServiceImpl.class.getMethod(
                "importDishes", MultipartFile.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "importDishes 缺少 @LogRecord 注解");
        assertEquals(DISH_IMPORT_TYPE, annotation.type());
        assertEquals(DISH_IMPORT_SUB_TYPE, annotation.subType());
    }

}
