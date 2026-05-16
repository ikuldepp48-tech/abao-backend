package cn.iocoder.yudao.module.restaurant.service.table;

import cn.iocoder.yudao.module.restaurant.controller.admin.table.vo.RestaurantTableBatchCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.table.vo.RestaurantTableCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.table.vo.RestaurantTableUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.table.RestaurantTableDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.table.RestaurantTableMapper;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RestaurantTableServiceImpl 单元测试
 *
 * 测试覆盖：正常路径 / @LogRecord 注解存在性
 * 覆盖方法：createTable / batchCreateTable / updateTable / deleteTable
 */
@ExtendWith(MockitoExtension.class)
class RestaurantTableServiceImplTest {

    @Mock
    private RestaurantTableMapper restaurantTableMapper;

    @Mock
    private RestaurantStoreMapper restaurantStoreMapper;

    @Mock
    private TableTokenService tableTokenService;

    @InjectMocks
    private RestaurantTableServiceImpl restaurantTableService;

    // ==================== createTable ====================

    @Test
    void createTable_正常路径_创建成功() {
        // 准备
        RestaurantTableCreateReqVO vo = new RestaurantTableCreateReqVO();
        vo.setStoreId(100L);
        vo.setArea("大厅");
        vo.setTableNo("A01");
        vo.setSeatCapacity(4);
        vo.setStatus(0);

        when(restaurantTableMapper.insert(any(RestaurantTableDO.class))).thenAnswer(inv -> {
            RestaurantTableDO arg = inv.getArgument(0);
            arg.setId(1L);
            return 1;
        });

        // 执行
        Long id = restaurantTableService.createTable(vo);

        // 验证
        assertNotNull(id, "创建成功应返回 ID");
        verify(restaurantTableMapper).insert(any(RestaurantTableDO.class));
    }

    // ==================== updateTable ====================

    @Test
    void updateTable_正常路径_更新成功() {
        // 准备
        RestaurantTableDO existing = RestaurantTableDO.builder()
                .id(1L)
                .storeId(100L)
                .tableNo("A01")
                .seatCapacity(4)
                .status(0)
                .build();

        when(restaurantTableMapper.selectById(1L)).thenReturn(existing);

        RestaurantTableUpdateReqVO vo = new RestaurantTableUpdateReqVO();
        vo.setId(1L);
        vo.setStoreId(100L);
        vo.setArea("包厢");
        vo.setTableNo("A01");
        vo.setSeatCapacity(6);
        vo.setStatus(0);

        // 执行
        restaurantTableService.updateTable(vo);

        // 验证
        verify(restaurantTableMapper).updateById(any(RestaurantTableDO.class));
    }

    // ==================== deleteTable ====================

    @Test
    void deleteTable_正常路径_删除成功() {
        // 准备
        RestaurantTableDO existing = RestaurantTableDO.builder()
                .id(1L)
                .storeId(100L)
                .build();

        when(restaurantTableMapper.selectById(1L)).thenReturn(existing);

        // 执行
        restaurantTableService.deleteTable(1L);

        // 验证
        verify(restaurantTableMapper).deleteById(1L);
    }

    // ==================== batchCreateTable ====================

    @Test
    void batchCreateTable_正常路径_批量创建成功() {
        // 准备
        RestaurantTableBatchCreateReqVO vo = new RestaurantTableBatchCreateReqVO();
        vo.setStoreId(100L);
        vo.setArea("大厅");
        vo.setPrefix("A");
        vo.setStartNo(1);
        vo.setEndNo(3);
        vo.setSeatCapacity(4);

        when(restaurantTableMapper.selectByStoreIdAndTableNo(anyLong(), anyString())).thenReturn(null);

        // 执行
        Map<String, Integer> result = restaurantTableService.batchCreateTable(vo);

        // 验证
        assertEquals(3, result.get("success").intValue(), "应成功创建 3 个桌台");
        assertEquals(0, result.get("skip").intValue(), "应跳过 0 个");
        verify(restaurantTableMapper, times(3)).insert(any(RestaurantTableDO.class));
    }

    // ==================== @LogRecord 注解验证 ====================

    @Test
    void createTable_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantTableServiceImpl.class.getMethod("createTable", RestaurantTableCreateReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "createTable 缺少 @LogRecord 注解");
        assertEquals("桌台", annotation.type());
        assertEquals("创建桌台", annotation.subType());
    }

    @Test
    void updateTable_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantTableServiceImpl.class.getMethod("updateTable", RestaurantTableUpdateReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "updateTable 缺少 @LogRecord 注解");
        assertEquals("桌台", annotation.type());
        assertEquals("更新桌台", annotation.subType());
    }

    @Test
    void deleteTable_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantTableServiceImpl.class.getMethod("deleteTable", Long.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "deleteTable 缺少 @LogRecord 注解");
        assertEquals("桌台", annotation.type());
        assertEquals("删除桌台", annotation.subType());
    }

    @Test
    void batchCreateTable_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantTableServiceImpl.class.getMethod("batchCreateTable", RestaurantTableBatchCreateReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "batchCreateTable 缺少 @LogRecord 注解");
        assertEquals("桌台", annotation.type());
        assertEquals("批量创建桌台", annotation.subType());
    }

}
