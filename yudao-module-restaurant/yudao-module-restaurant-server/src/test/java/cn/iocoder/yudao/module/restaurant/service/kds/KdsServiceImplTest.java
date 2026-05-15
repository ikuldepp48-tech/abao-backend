package cn.iocoder.yudao.module.restaurant.service.kds;

import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.kitchen.RestaurantKitchenStationDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderItemDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderLogDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSpuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.kitchen.RestaurantKitchenStationMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderItemMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderLogMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderMapper;
import cn.iocoder.yudao.module.restaurant.service.kitchen.KdsPushService;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import org.mockito.ArgumentMatchers;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * KdsServiceImpl 单元测试
 *
 * 测试覆盖：正常路径 / 异常路径 / 并发路径 / @LogRecord 注解验证
 * 覆盖方法：startItem / finishItem
 */
@ExtendWith(MockitoExtension.class)
class KdsServiceImplTest {

    @Mock
    private RestaurantOrderItemMapper orderItemMapper;

    @Mock
    private RestaurantOrderMapper orderMapper;

    @Mock
    private RestaurantOrderLogMapper orderLogMapper;

    @Mock
    private RestaurantKitchenStationMapper stationMapper;

    @Mock
    private RestaurantDishSpuMapper dishSpuMapper;

    @Mock
    private KdsPushService kdsPushService;

    @InjectMocks
    private KdsServiceImpl kdsService;

    private RestaurantOrderItemDO mockItem;
    private RestaurantOrderDO mockOrder;

    @BeforeEach
    void setUp() {
        mockItem = new RestaurantOrderItemDO();
        mockItem.setId(100L);
        mockItem.setOrderId(200L);
        mockItem.setSpuId(10L);
        mockItem.setSkuId(1L);
        mockItem.setSkuName("测试SKU");
        mockItem.setSpuName("测试菜品");
        mockItem.setKdsStatus(0);

        mockOrder = new RestaurantOrderDO();
        mockOrder.setId(200L);
        mockOrder.setStatus(1); // 已支付
        mockOrder.setOrderNo("RT20260515001");
    }

    // ==================== startItem ====================

    @Test
    void startItem_正常路径_状态0变1且订单1变2() {
        when(orderItemMapper.selectById(100L)).thenReturn(mockItem);
        when(orderMapper.selectById(200L)).thenReturn(mockOrder);
        // pushStatusToStation 会查 SPU 和 station
        RestaurantDishSpuDO spu = new RestaurantDishSpuDO();
        spu.setId(10L);
        spu.setCategoryId(5L);
        when(dishSpuMapper.selectById(10L)).thenReturn(spu);
        RestaurantKitchenStationDO station = new RestaurantKitchenStationDO();
        station.setId(1L);
        station.setDishCategories("[5]");
        station.setStatus(1);
        when(stationMapper.selectList(any())).thenReturn(Collections.singletonList(station));

        kdsService.startItem(100L);

        verify(orderItemMapper).selectById(100L);
        verify(orderItemMapper).updateById(mockItem);
        assertEquals(1, mockItem.getKdsStatus());
        verify(orderMapper).selectById(200L);
        verify(orderMapper).updateById(mockOrder);
        assertEquals(2, mockOrder.getStatus());
        verify(orderLogMapper, times(1)).insert(ArgumentMatchers.<RestaurantOrderLogDO>any());
        verify(kdsPushService).pushStatusUpdate(eq(1L), eq(200L), eq(100L), eq(1));
    }

    @Test
    void startItem_正常路径_订单不是状态1不升级订单() {
        mockOrder.setStatus(2); // 已是备餐中，不重复升级
        when(orderItemMapper.selectById(100L)).thenReturn(mockItem);
        when(orderMapper.selectById(200L)).thenReturn(mockOrder);
        RestaurantDishSpuDO spu = new RestaurantDishSpuDO();
        spu.setId(10L);
        spu.setCategoryId(5L);
        when(dishSpuMapper.selectById(10L)).thenReturn(spu);
        when(stationMapper.selectList(any())).thenReturn(Collections.emptyList());

        kdsService.startItem(100L);

        verify(orderItemMapper).updateById(mockItem);
        // 订单不应被升级
        verify(orderMapper, never()).updateById(any(RestaurantOrderDO.class));
        verify(orderLogMapper, never()).insert(ArgumentMatchers.<RestaurantOrderLogDO>any());
    }

    @Test
    void startItem_异常路径_订单项不存在() {
        when(orderItemMapper.selectById(999L)).thenReturn(null);

        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () ->
                kdsService.startItem(999L),
                "不存在的订单项应抛出 ServiceException");
    }

    @Test
    void startItem_异常路径_KDS状态不是0() {
        mockItem.setKdsStatus(1); // 已在制作中
        when(orderItemMapper.selectById(100L)).thenReturn(mockItem);

        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () ->
                kdsService.startItem(100L),
                "状态不为0的订单项应抛出异常");
    }

    @Test
    void startItem_并发路径_两个不同订单项同时开始() {
        RestaurantOrderItemDO item2 = new RestaurantOrderItemDO();
        item2.setId(101L);
        item2.setOrderId(201L);
        item2.setSpuId(20L);
        item2.setKdsStatus(0);

        RestaurantOrderDO order2 = new RestaurantOrderDO();
        order2.setId(201L);
        order2.setStatus(1);

        when(orderItemMapper.selectById(100L)).thenReturn(mockItem);
        when(orderItemMapper.selectById(101L)).thenReturn(item2);
        when(orderMapper.selectById(200L)).thenReturn(mockOrder);
        when(orderMapper.selectById(201L)).thenReturn(order2);

        RestaurantDishSpuDO spu = new RestaurantDishSpuDO();
        spu.setId(10L);
        spu.setCategoryId(5L);
        when(dishSpuMapper.selectById(anyLong())).thenReturn(spu);
        when(stationMapper.selectList(any())).thenReturn(Collections.emptyList());

        kdsService.startItem(100L);
        kdsService.startItem(101L);

        assertEquals(1, mockItem.getKdsStatus());
        assertEquals(1, item2.getKdsStatus());
        verify(orderItemMapper, times(2)).updateById(any(RestaurantOrderItemDO.class));
    }

    // ==================== finishItem ====================

    @Test
    void finishItem_正常路径_状态1变2且全部出餐订单升3() {
        mockItem.setKdsStatus(1); // 制作中
        mockOrder.setStatus(2); // 备餐中 → 待升级为 3(READY)
        when(orderItemMapper.selectById(100L)).thenReturn(mockItem);
        // 全部 item 都已完成
        RestaurantOrderItemDO doneItem = new RestaurantOrderItemDO();
        doneItem.setId(100L);
        doneItem.setKdsStatus(2); // 更新后状态
        when(orderItemMapper.selectListByOrderId(200L)).thenReturn(Collections.singletonList(doneItem));
        when(orderMapper.selectById(200L)).thenReturn(mockOrder);
        RestaurantDishSpuDO spu = new RestaurantDishSpuDO();
        spu.setId(10L);
        spu.setCategoryId(5L);
        when(dishSpuMapper.selectById(10L)).thenReturn(spu);
        when(stationMapper.selectList(any())).thenReturn(Collections.emptyList());

        kdsService.finishItem(100L);

        verify(orderItemMapper).updateById(mockItem);
        assertEquals(2, mockItem.getKdsStatus());
        verify(orderMapper).updateById(mockOrder);
        assertEquals(3, mockOrder.getStatus());
        verify(orderLogMapper, times(1)).insert(ArgumentMatchers.<RestaurantOrderLogDO>any());
    }

    @Test
    void finishItem_正常路径_未全部出餐不升级订单() {
        mockItem.setKdsStatus(1);
        when(orderItemMapper.selectById(100L)).thenReturn(mockItem);
        // 还有其他 item 没完成
        RestaurantOrderItemDO otherItem = new RestaurantOrderItemDO();
        otherItem.setId(101L);
        otherItem.setKdsStatus(1); // 还在制作中
        when(orderItemMapper.selectListByOrderId(200L)).thenReturn(Arrays.asList(mockItem, otherItem));
        RestaurantDishSpuDO spu = new RestaurantDishSpuDO();
        spu.setId(10L);
        spu.setCategoryId(5L);
        when(dishSpuMapper.selectById(10L)).thenReturn(spu);
        when(stationMapper.selectList(any())).thenReturn(Collections.emptyList());

        kdsService.finishItem(100L);

        verify(orderItemMapper).updateById(mockItem);
        assertEquals(2, mockItem.getKdsStatus());
        // 订单不应升级
        verify(orderMapper, never()).updateById(any(RestaurantOrderDO.class));
        verify(orderLogMapper, never()).insert(ArgumentMatchers.<RestaurantOrderLogDO>any());
    }

    @Test
    void finishItem_异常路径_订单项不存在() {
        when(orderItemMapper.selectById(999L)).thenReturn(null);

        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () ->
                kdsService.finishItem(999L),
                "不存在的订单项应抛出 ServiceException");
    }

    @Test
    void finishItem_异常路径_KDS状态不是1() {
        mockItem.setKdsStatus(0); // 还没开始制作
        when(orderItemMapper.selectById(100L)).thenReturn(mockItem);

        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () ->
                kdsService.finishItem(100L),
                "状态不为1的订单项应抛出异常");
    }

    @Test
    void finishItem_异常路径_KDS状态已是2() {
        mockItem.setKdsStatus(2); // 已出餐
        when(orderItemMapper.selectById(100L)).thenReturn(mockItem);

        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () ->
                kdsService.finishItem(100L),
                "已出餐的订单项应抛出异常");
    }

    @Test
    void finishItem_并发路径_两个不同订单项同时出餐() {
        mockItem.setKdsStatus(1);
        RestaurantOrderItemDO item2 = new RestaurantOrderItemDO();
        item2.setId(101L);
        item2.setOrderId(201L);
        item2.setSpuId(20L);
        item2.setKdsStatus(1);

        RestaurantOrderDO order2 = new RestaurantOrderDO();
        order2.setId(201L);
        order2.setStatus(2);

        when(orderItemMapper.selectById(100L)).thenReturn(mockItem);
        when(orderItemMapper.selectById(101L)).thenReturn(item2);
        when(orderItemMapper.selectListByOrderId(200L)).thenReturn(Collections.emptyList());
        when(orderItemMapper.selectListByOrderId(201L)).thenReturn(Collections.emptyList());

        RestaurantDishSpuDO spu = new RestaurantDishSpuDO();
        spu.setId(10L);
        spu.setCategoryId(5L);
        when(dishSpuMapper.selectById(anyLong())).thenReturn(spu);
        when(stationMapper.selectList(any())).thenReturn(Collections.emptyList());

        kdsService.finishItem(100L);
        kdsService.finishItem(101L);

        assertEquals(2, mockItem.getKdsStatus());
        assertEquals(2, item2.getKdsStatus());
        verify(orderItemMapper, times(2)).updateById(any(RestaurantOrderItemDO.class));
    }

    // ==================== @LogRecord 注解验证 ====================

    @Test
    void startItem_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = KdsServiceImpl.class.getMethod("startItem", Long.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "startItem 缺少 @LogRecord 注解");
        assertEquals("KDS", annotation.type());
        assertEquals("开始制作", annotation.subType());
    }

    @Test
    void finishItem_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = KdsServiceImpl.class.getMethod("finishItem", Long.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "finishItem 缺少 @LogRecord 注解");
        assertEquals("KDS", annotation.type());
        assertEquals("完成出餐", annotation.subType());
    }
}
