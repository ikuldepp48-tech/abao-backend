package cn.iocoder.yudao.module.restaurant.service.store;

import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDishDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreDishMapper;
import cn.iocoder.yudao.module.restaurant.service.menu.MenuCacheEvictEvent;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * RestaurantStoreDishServiceImpl 单元测试
 *
 * 测试覆盖：正常路径 / 异常路径 / @LogRecord 注解存在性
 * 覆盖方法：batchSoldOut / batchRestore / batchUpdateStatus / overridePrice
 */
@ExtendWith(MockitoExtension.class)
class RestaurantStoreDishServiceImplTest {

    @Mock
    private RestaurantStoreDishMapper storeDishMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RestaurantStoreDishServiceImpl storeDishService;

    private RestaurantStoreDishDO mockDish;

    @BeforeEach
    void setUp() {
        mockDish = new RestaurantStoreDishDO();
        mockDish.setId(1L);
        mockDish.setStoreId(100L);
        mockDish.setIsSoldOut(false);
        mockDish.setPrice(BigDecimal.valueOf(25.00));
        mockDish.setStatus(1);
    }

    // ==================== batchSoldOut ====================

    @Test
    void batchSoldOut_正常路径_批量沽清成功() {
        List<Long> ids = Arrays.asList(1L, 2L, 3L);
        when(storeDishMapper.selectById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            RestaurantStoreDishDO d = new RestaurantStoreDishDO();
            d.setId(id);
            d.setStoreId(100L + id);
            return d;
        });

        int count = storeDishService.batchSoldOut(ids);

        assertEquals(3, count, "应该沽清 3 个菜品");
        verify(eventPublisher).publishEvent(any(MenuCacheEvictEvent.class));
    }

    @Test
    void batchSoldOut_异常路径_空列表不崩溃() {
        int count = storeDishService.batchSoldOut(List.of());
        assertEquals(0, count, "空列表应该返回 0");
    }

    @Test
    void batchSoldOut_并发路径_部分ID不存在时跳过() {
        List<Long> ids = Arrays.asList(1L, 999L);
        when(storeDishMapper.selectById(1L)).thenReturn(mockDish);
        when(storeDishMapper.selectById(999L)).thenReturn(null);

        int count = storeDishService.batchSoldOut(ids);

        assertEquals(1, count, "只应沽清存在的 1 个菜品");
        verify(eventPublisher).publishEvent(any(MenuCacheEvictEvent.class));
    }

    // ==================== batchRestore ====================

    @Test
    void batchRestore_正常路径_批量恢复供应成功_清空今日销量() {
        List<Long> ids = Arrays.asList(1L);
        mockDish.setIsSoldOut(true);
        mockDish.setTodaySold(50);
        when(storeDishMapper.selectById(1L)).thenReturn(mockDish);

        int count = storeDishService.batchRestore(ids);

        assertEquals(1, count);
        // 验证状态已恢复
        assertFalse(mockDish.getIsSoldOut(), "沽清状态应设为 false");
        assertEquals(0, mockDish.getTodaySold(), "今日销量应清零");
        verify(eventPublisher).publishEvent(any(MenuCacheEvictEvent.class));
    }

    @Test
    void batchRestore_异常路径_空列表不崩溃() {
        int count = storeDishService.batchRestore(List.of());
        assertEquals(0, count);
    }

    @Test
    void batchRestore_并发路径_DO不存在时跳过() {
        when(storeDishMapper.selectById(999L)).thenReturn(null);

        int count = storeDishService.batchRestore(List.of(999L));

        assertEquals(0, count, "不存在的菜品应跳过");
    }

    // ==================== batchUpdateStatus ====================

    @Test
    void batchUpdateStatus_正常路径_批量更新状态成功() {
        when(storeDishMapper.selectById(1L)).thenReturn(mockDish);

        int count = storeDishService.batchUpdateStatus(List.of(1L), 0);

        assertEquals(1, count);
        assertEquals(0, mockDish.getStatus(), "状态应更新为 0（下架）");
        verify(eventPublisher).publishEvent(any(MenuCacheEvictEvent.class));
    }

    @Test
    void batchUpdateStatus_并发路径_部分不存在时跳过() {
        when(storeDishMapper.selectById(1L)).thenReturn(mockDish);
        when(storeDishMapper.selectById(999L)).thenReturn(null);

        int count = storeDishService.batchUpdateStatus(Arrays.asList(1L, 999L), 0);

        assertEquals(1, count, "只应更新存在的 1 个");
    }

    // ==================== overridePrice ====================

    @Test
    void overridePrice_正常路径_覆盖价格成功() {
        when(storeDishMapper.selectById(1L)).thenReturn(mockDish);

        int result = storeDishService.overridePrice(1L, BigDecimal.valueOf(35.00));

        assertEquals(1, result);
        assertEquals(BigDecimal.valueOf(35.00), mockDish.getPrice(), "价格应更新为 35.00");
        verify(eventPublisher).publishEvent(any(MenuCacheEvictEvent.class));
    }

    @Test
    void overridePrice_异常路径_菜品不存在抛异常() {
        when(storeDishMapper.selectById(999L)).thenReturn(null);

        assertThrows(Exception.class, () ->
                storeDishService.overridePrice(999L, BigDecimal.valueOf(35.00)),
                "不存在的菜品应抛出异常");
    }

    @Test
    void overridePrice_并发路径_同时覆盖不同菜品价格互不影响() {
        RestaurantStoreDishDO dish2 = new RestaurantStoreDishDO();
        dish2.setId(2L);
        dish2.setStoreId(200L);
        dish2.setPrice(BigDecimal.valueOf(30.00));
        when(storeDishMapper.selectById(1L)).thenReturn(mockDish);
        when(storeDishMapper.selectById(2L)).thenReturn(dish2);

        int r1 = storeDishService.overridePrice(1L, BigDecimal.valueOf(40.00));
        int r2 = storeDishService.overridePrice(2L, BigDecimal.valueOf(50.00));

        assertEquals(1, r1);
        assertEquals(1, r2);
        assertEquals(BigDecimal.valueOf(40.00), mockDish.getPrice());
        assertEquals(BigDecimal.valueOf(50.00), dish2.getPrice());
    }

    // ==================== @LogRecord 注解验证 ====================

    @Test
    void batchSoldOut_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantStoreDishServiceImpl.class.getMethod("batchSoldOut", List.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "batchSoldOut 缺少 @LogRecord 注解");
        assertEquals("门店菜品", annotation.type());
        assertEquals("一键沽清", annotation.subType());
    }

    @Test
    void batchRestore_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantStoreDishServiceImpl.class.getMethod("batchRestore", List.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "batchRestore 缺少 @LogRecord 注解");
        assertEquals("门店菜品", annotation.type());
        assertEquals("批量恢复", annotation.subType());
    }

    @Test
    void batchUpdateStatus_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantStoreDishServiceImpl.class.getMethod("batchUpdateStatus", List.class, Integer.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "batchUpdateStatus 缺少 @LogRecord 注解");
        assertEquals("门店菜品", annotation.type());
        assertEquals("批量上下架", annotation.subType());
    }

    @Test
    void overridePrice_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantStoreDishServiceImpl.class.getMethod("overridePrice", Long.class, BigDecimal.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "overridePrice 缺少 @LogRecord 注解");
        assertEquals("门店菜品", annotation.type());
        assertEquals("覆盖价格", annotation.subType());
    }
}
