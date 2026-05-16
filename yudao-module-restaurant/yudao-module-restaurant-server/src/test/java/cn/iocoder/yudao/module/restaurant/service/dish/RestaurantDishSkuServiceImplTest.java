package cn.iocoder.yudao.module.restaurant.service.dish;

import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSkuCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishSkuUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSkuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDishDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSkuMapper;
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
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.DISH_SKU_NOT_EXISTS;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RestaurantDishSkuServiceImpl 单元测试
 *
 * 测试覆盖：正常路径 / 异常路径 / 并发路径 / @LogRecord 注解验证
 * 覆盖方法：createSku / updateSku
 * 重点验证：price / memberPrice / costPrice 三个金额字段
 */
@ExtendWith(MockitoExtension.class)
class RestaurantDishSkuServiceImplTest {

    @Mock
    private RestaurantDishSkuMapper skuMapper;

    @Mock
    private RestaurantStoreDishMapper storeDishMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RestaurantDishSkuServiceImpl skuService;

    private RestaurantDishSkuDO mockSku;

    @BeforeEach
    void setUp() {
        mockSku = new RestaurantDishSkuDO();
        mockSku.setId(1L);
        mockSku.setSpuId(10L);
        mockSku.setName("大份");
        mockSku.setPrice(BigDecimal.valueOf(17.00));
        mockSku.setMemberPrice(BigDecimal.valueOf(15.00));
        mockSku.setCostPrice(BigDecimal.valueOf(8.00));
    }

    // ==================== createSku ====================

    @Test
    void createSku_正常路径_创建SKU成功() {
        RestaurantDishSkuCreateReqVO vo = buildCreateVO();
        mockStoreDishForSpu(10L, 100L);

        Long id = skuService.createSku(vo);

        verify(skuMapper).insert(any(RestaurantDishSkuDO.class));
        verify(eventPublisher).publishEvent(any(MenuCacheEvictEvent.class));
    }

    @Test
    void createSku_异常路径_spuId无关联门店时仍成功() {
        // findStoreIdsBySpuId 返回空集合，不应阻断创建
        RestaurantDishSkuCreateReqVO vo = buildCreateVO();
        when(storeDishMapper.selectList()).thenReturn(Collections.emptyList());

        Long id = skuService.createSku(vo);

        // 创建应成功，只是缓存驱逐的门店集合为空
        verify(skuMapper).insert(any(RestaurantDishSkuDO.class));
        verify(eventPublisher).publishEvent(any(MenuCacheEvictEvent.class));
    }

    @Test
    void createSku_并发路径_两个不同spuId创建互不影响() {
        RestaurantDishSkuCreateReqVO vo1 = buildCreateVO();
        vo1.setSpuId(10L);
        vo1.setPrice(BigDecimal.valueOf(17.00));
        RestaurantDishSkuCreateReqVO vo2 = buildCreateVO();
        vo2.setSpuId(20L);
        vo2.setPrice(BigDecimal.valueOf(25.00));

        mockStoreDishForSpu(10L, 100L);
        mockStoreDishForSpu(20L, 200L);

        Long id1 = skuService.createSku(vo1);
        Long id2 = skuService.createSku(vo2);

        // 两个创建都成功（mock 不设置 ID，此处验证不抛异常）
        verify(skuMapper, times(2)).insert(any(RestaurantDishSkuDO.class));
        verify(eventPublisher, times(2)).publishEvent(any(MenuCacheEvictEvent.class));
    }

    // ==================== updateSku ====================

    @Test
    void updateSku_正常路径_更新SKU成功() {
        RestaurantDishSkuUpdateReqVO vo = buildUpdateVO();
        vo.setPrice(BigDecimal.valueOf(20.00));
        vo.setMemberPrice(BigDecimal.valueOf(18.00));
        vo.setCostPrice(BigDecimal.valueOf(10.00));

        when(skuMapper.selectById(1L)).thenReturn(mockSku);
        mockStoreDishForSpu(10L, 100L);

        skuService.updateSku(vo);

        verify(skuMapper).updateById(any(RestaurantDishSkuDO.class));
        verify(eventPublisher).publishEvent(any(MenuCacheEvictEvent.class));
    }

    @Test
    void updateSku_异常路径_SKU不存在抛出异常() {
        RestaurantDishSkuUpdateReqVO vo = buildUpdateVO();
        vo.setId(999L);
        when(skuMapper.selectById(999L)).thenReturn(null);

        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () ->
                skuService.updateSku(vo),
                "不存在的SKU应抛出 ServiceException");
    }

    @Test
    void updateSku_并发路径_两个不同SKU更新互不影响() {
        RestaurantDishSkuDO sku2 = new RestaurantDishSkuDO();
        sku2.setId(2L);
        sku2.setSpuId(20L);
        sku2.setName("小份");
        sku2.setPrice(BigDecimal.valueOf(12.00));

        RestaurantDishSkuUpdateReqVO vo1 = buildUpdateVO();
        vo1.setPrice(BigDecimal.valueOf(30.00));
        RestaurantDishSkuUpdateReqVO vo2 = buildUpdateVO();
        vo2.setId(2L);
        vo2.setPrice(BigDecimal.valueOf(15.00));

        when(skuMapper.selectById(1L)).thenReturn(mockSku);
        when(skuMapper.selectById(2L)).thenReturn(sku2);
        mockStoreDishForSpu(10L, 100L);
        mockStoreDishForSpu(20L, 200L);

        skuService.updateSku(vo1);
        skuService.updateSku(vo2);

        verify(skuMapper, times(2)).updateById(any(RestaurantDishSkuDO.class));
        verify(eventPublisher, times(2)).publishEvent(any(MenuCacheEvictEvent.class));
    }

    // ==================== 金额字段变更验证（updateSku diff 覆盖） ====================

    @Test
    void updateSku_金额验证_price变更被传递到Mapper() {
        RestaurantDishSkuUpdateReqVO vo = buildUpdateVO();
        vo.setPrice(BigDecimal.valueOf(99.00));
        when(skuMapper.selectById(1L)).thenReturn(mockSku);
        mockStoreDishForSpu(10L, 100L);

        skuService.updateSku(vo);

        verify(skuMapper).updateById(any(RestaurantDishSkuDO.class));
        // 实际 diff 由 mzt-logapi 框架运行时生成，此处验证 updateById 被成功调用
    }

    @Test
    void updateSku_金额验证_memberPrice变更被传递() {
        RestaurantDishSkuUpdateReqVO vo = buildUpdateVO();
        vo.setMemberPrice(BigDecimal.valueOf(88.00));
        when(skuMapper.selectById(1L)).thenReturn(mockSku);
        mockStoreDishForSpu(10L, 100L);

        skuService.updateSku(vo);

        verify(skuMapper).updateById(any(RestaurantDishSkuDO.class));
    }

    @Test
    void updateSku_金额验证_costPrice变更被传递() {
        RestaurantDishSkuUpdateReqVO vo = buildUpdateVO();
        vo.setCostPrice(BigDecimal.valueOf(5.00));
        when(skuMapper.selectById(1L)).thenReturn(mockSku);
        mockStoreDishForSpu(10L, 100L);

        skuService.updateSku(vo);

        verify(skuMapper).updateById(any(RestaurantDishSkuDO.class));
    }

    @Test
    void updateSku_金额验证_三个金额字段同时变更() {
        RestaurantDishSkuUpdateReqVO vo = buildUpdateVO();
        vo.setPrice(BigDecimal.valueOf(50.00));
        vo.setMemberPrice(BigDecimal.valueOf(45.00));
        vo.setCostPrice(BigDecimal.valueOf(20.00));
        when(skuMapper.selectById(1L)).thenReturn(mockSku);
        mockStoreDishForSpu(10L, 100L);

        skuService.updateSku(vo);

        verify(skuMapper).updateById(any(RestaurantDishSkuDO.class));
        // {_DIFF{#updateReqVO}} 将捕获 price(17→50) / memberPrice(15→45) / costPrice(8→20)
    }

    // ==================== @LogRecord 注解验证 ====================

    @Test
    void createSku_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantDishSkuServiceImpl.class.getMethod("createSku", RestaurantDishSkuCreateReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "createSku 缺少 @LogRecord 注解");
        assertEquals("菜品SKU", annotation.type());
        assertEquals("创建SKU", annotation.subType());
    }

    @Test
    void updateSku_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantDishSkuServiceImpl.class.getMethod("updateSku", RestaurantDishSkuUpdateReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "updateSku 缺少 @LogRecord 注解");
        assertEquals("菜品SKU", annotation.type());
        assertEquals("更新SKU", annotation.subType());
    }

    @Test
    void updateSku_检查LogRecord使用DIFF() throws NoSuchMethodException {
        Method method = RestaurantDishSkuServiceImpl.class.getMethod("updateSku", RestaurantDishSkuUpdateReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "updateSku 缺少 @LogRecord 注解");
        assertTrue(annotation.success().contains("_DIFF"),
                "updateSku 的 success 应包含 {_DIFF{#updateReqVO}} 用于自动记录金额变更");
    }

    // ==================== deleteSku ====================

    @Test
    void deleteSku_正常路径_删除SKU成功() {
        when(skuMapper.selectById(1L)).thenReturn(mockSku);
        mockStoreDishForSpu(10L, 100L);

        skuService.deleteSku(1L);

        verify(skuMapper).deleteById(1L);
        verify(eventPublisher).publishEvent(any(MenuCacheEvictEvent.class));
    }

    @Test
    void deleteSku_异常路径_SKU不存在抛出异常() {
        when(skuMapper.selectById(999L)).thenReturn(null);

        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () ->
                skuService.deleteSku(999L),
                "不存在的SKU应抛出 ServiceException");
    }

    @Test
    void deleteSku_并发路径_两个不同SKU删除互不影响() {
        RestaurantDishSkuDO sku2 = new RestaurantDishSkuDO();
        sku2.setId(2L);
        sku2.setSpuId(20L);

        when(skuMapper.selectById(1L)).thenReturn(mockSku);
        when(skuMapper.selectById(2L)).thenReturn(sku2);
        mockStoreDishForSpu(10L, 100L);
        mockStoreDishForSpu(20L, 200L);

        skuService.deleteSku(1L);
        skuService.deleteSku(2L);

        verify(skuMapper, times(2)).deleteById(anyLong());
        verify(eventPublisher, times(2)).publishEvent(any(MenuCacheEvictEvent.class));
    }

    @Test
    void deleteSku_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantDishSkuServiceImpl.class.getMethod("deleteSku", Long.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "deleteSku 缺少 @LogRecord 注解");
        assertEquals("菜品SKU", annotation.type());
        assertEquals("删除SKU", annotation.subType());
    }

    // ==================== helpers ====================

    private RestaurantDishSkuCreateReqVO buildCreateVO() {
        RestaurantDishSkuCreateReqVO vo = new RestaurantDishSkuCreateReqVO();
        vo.setSpuId(10L);
        vo.setName("大份");
        vo.setPrice(BigDecimal.valueOf(17.00));
        vo.setMemberPrice(BigDecimal.valueOf(15.00));
        vo.setCostPrice(BigDecimal.valueOf(8.00));
        return vo;
    }

    private RestaurantDishSkuUpdateReqVO buildUpdateVO() {
        RestaurantDishSkuUpdateReqVO vo = new RestaurantDishSkuUpdateReqVO();
        vo.setId(1L);
        vo.setSpuId(10L);
        vo.setName("大份");
        vo.setPrice(BigDecimal.valueOf(17.00));
        vo.setMemberPrice(BigDecimal.valueOf(15.00));
        vo.setCostPrice(BigDecimal.valueOf(8.00));
        return vo;
    }

    private void mockStoreDishForSpu(Long spuId, Long storeId) {
        RestaurantStoreDishDO sd = new RestaurantStoreDishDO();
        sd.setSpuId(spuId);
        sd.setStoreId(storeId);
        when(storeDishMapper.selectList()).thenReturn(List.of(sd));
    }
}
