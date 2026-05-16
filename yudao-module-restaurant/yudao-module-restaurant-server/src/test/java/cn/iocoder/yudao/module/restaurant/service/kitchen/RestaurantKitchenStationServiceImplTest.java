package cn.iocoder.yudao.module.restaurant.service.kitchen;

import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.restaurant.controller.admin.kitchen.vo.RestaurantKitchenStationSaveReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.kitchen.RestaurantKitchenStationDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.kitchen.RestaurantKitchenStationMapper;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RestaurantKitchenStationServiceImpl 单元测试
 *
 * 测试覆盖：正常路径 / @LogRecord 注解存在性
 * 覆盖方法：createStation / updateStation / deleteStation
 */
@ExtendWith(MockitoExtension.class)
class RestaurantKitchenStationServiceImplTest {

    @Mock
    private RestaurantKitchenStationMapper stationMapper;

    @InjectMocks
    private RestaurantKitchenStationServiceImpl stationService;

    // ==================== createStation ====================

    @Test
    void createStation_正常路径_创建成功() {
        // 准备
        RestaurantKitchenStationSaveReqVO vo = new RestaurantKitchenStationSaveReqVO();
        vo.setName("炸鸡档口");
        vo.setDishCategories(Arrays.asList(1L, 2L));
        vo.setSort(1);
        vo.setStatus(1);

        when(stationMapper.insert(any(RestaurantKitchenStationDO.class))).thenAnswer(inv -> {
            RestaurantKitchenStationDO arg = inv.getArgument(0);
            arg.setId(1L);
            return 1;
        });

        // 执行
        Long id = stationService.createStation(vo);

        // 验证
        assertNotNull(id, "创建成功应返回 ID");
        verify(stationMapper).insert(any(RestaurantKitchenStationDO.class));
    }

    // ==================== updateStation ====================

    @Test
    void updateStation_正常路径_更新成功() {
        // 准备
        RestaurantKitchenStationDO existing = RestaurantKitchenStationDO.builder()
                .id(1L)
                .name("旧档口")
                .dishCategories(JSONUtil.toJsonStr(Arrays.asList(3L)))
                .sort(1)
                .status(1)
                .build();

        when(stationMapper.selectById(1L)).thenReturn(existing);

        RestaurantKitchenStationSaveReqVO vo = new RestaurantKitchenStationSaveReqVO();
        vo.setId(1L);
        vo.setName("炸鸡档口");
        vo.setDishCategories(Arrays.asList(1L, 2L));
        vo.setSort(2);
        vo.setStatus(0);

        // 执行
        stationService.updateStation(vo);

        // 验证
        verify(stationMapper).updateById(any(RestaurantKitchenStationDO.class));
    }

    // ==================== deleteStation ====================

    @Test
    void deleteStation_正常路径_删除成功() {
        // 执行
        stationService.deleteStation(1L);

        // 验证
        verify(stationMapper).deleteById(1L);
    }

    // ==================== @LogRecord 注解验证 ====================

    @Test
    void createStation_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantKitchenStationServiceImpl.class.getMethod("createStation", RestaurantKitchenStationSaveReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "createStation 缺少 @LogRecord 注解");
        assertEquals("厨房档口", annotation.type());
        assertEquals("创建档口", annotation.subType());
    }

    @Test
    void updateStation_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantKitchenStationServiceImpl.class.getMethod("updateStation", RestaurantKitchenStationSaveReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "updateStation 缺少 @LogRecord 注解");
        assertEquals("厨房档口", annotation.type());
        assertEquals("更新档口", annotation.subType());
    }

    @Test
    void deleteStation_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantKitchenStationServiceImpl.class.getMethod("deleteStation", Long.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "deleteStation 缺少 @LogRecord 注解");
        assertEquals("厨房档口", annotation.type());
        assertEquals("删除档口", annotation.subType());
    }

}
