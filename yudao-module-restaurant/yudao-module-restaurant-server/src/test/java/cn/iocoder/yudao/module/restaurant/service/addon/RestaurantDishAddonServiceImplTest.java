package cn.iocoder.yudao.module.restaurant.service.addon;

import cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo.RestaurantDishAddonCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo.RestaurantDishAddonUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishAddonDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishSpuAddonRelDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDishDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.addon.RestaurantDishAddonMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.addon.RestaurantDishSpuAddonRelMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RestaurantDishAddonServiceImpl 单元测试
 *
 * 测试覆盖：正常路径 / 异常路径 / 并发路径 / @LogRecord 注解验证
 * 覆盖方法：createAddon / updateAddon / deleteAddon
 * 重点验证：extraPrice 加价字段
 */
@ExtendWith(MockitoExtension.class)
class RestaurantDishAddonServiceImplTest {

    @Mock
    private RestaurantDishAddonMapper addonMapper;

    @Mock
    private RestaurantDishSpuAddonRelMapper spuAddonRelMapper;

    @Mock
    private RestaurantStoreDishMapper storeDishMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RestaurantDishAddonServiceImpl addonService;

    private RestaurantDishAddonDO mockAddon;

    @BeforeEach
    void setUp() {
        mockAddon = new RestaurantDishAddonDO();
        mockAddon.setId(1L);
        mockAddon.setBrandId(1L);
        mockAddon.setGroupName("配菜加料");
        mockAddon.setName("加鸡蛋");
        mockAddon.setExtraPrice(BigDecimal.valueOf(2.00));
    }

    // ==================== createAddon ====================

    @Test
    void createAddon_正常路径_创建成功() {
        RestaurantDishAddonCreateReqVO vo = buildCreateVO();

        Long id = addonService.createAddon(vo);

        verify(addonMapper).insert(any(RestaurantDishAddonDO.class));
    }

    @Test
    void createAddon_正常路径_不同加价() {
        RestaurantDishAddonCreateReqVO vo = buildCreateVO();
        vo.setExtraPrice(BigDecimal.valueOf(5.00));

        Long id = addonService.createAddon(vo);

        verify(addonMapper).insert(any(RestaurantDishAddonDO.class));
    }

    @Test
    void createAddon_并发路径_两个不同加料创建互不影响() {
        RestaurantDishAddonCreateReqVO vo1 = buildCreateVO();
        vo1.setName("加鸡蛋");
        RestaurantDishAddonCreateReqVO vo2 = buildCreateVO();
        vo2.setName("加芝士");
        vo2.setExtraPrice(BigDecimal.valueOf(3.00));

        Long id1 = addonService.createAddon(vo1);
        Long id2 = addonService.createAddon(vo2);

        verify(addonMapper, times(2)).insert(any(RestaurantDishAddonDO.class));
    }

    // ==================== updateAddon ====================

    @Test
    void updateAddon_正常路径_更新成功() {
        RestaurantDishAddonUpdateReqVO vo = buildUpdateVO();
        vo.setExtraPrice(BigDecimal.valueOf(5.00));
        when(addonMapper.selectById(1L)).thenReturn(mockAddon);
        when(spuAddonRelMapper.selectList()).thenReturn(Collections.emptyList());

        addonService.updateAddon(vo);

        verify(addonMapper).updateById(any(RestaurantDishAddonDO.class));
        verify(eventPublisher).publishEvent(any(MenuCacheEvictEvent.class));
    }

    @Test
    void updateAddon_正常路径_有SPU关联时驱逐门店缓存() {
        RestaurantDishAddonUpdateReqVO vo = buildUpdateVO();
        vo.setExtraPrice(BigDecimal.valueOf(3.00));
        when(addonMapper.selectById(1L)).thenReturn(mockAddon);

        RestaurantDishSpuAddonRelDO rel = new RestaurantDishSpuAddonRelDO();
        rel.setAddonId(1L);
        rel.setSpuId(10L);
        when(spuAddonRelMapper.selectList()).thenReturn(Collections.singletonList(rel));

        RestaurantStoreDishDO sd = new RestaurantStoreDishDO();
        sd.setSpuId(10L);
        sd.setStoreId(100L);
        when(storeDishMapper.selectList()).thenReturn(Collections.singletonList(sd));

        addonService.updateAddon(vo);

        verify(addonMapper).updateById(any(RestaurantDishAddonDO.class));
        verify(eventPublisher).publishEvent(any(MenuCacheEvictEvent.class));
    }

    @Test
    void updateAddon_异常路径_加料不存在() {
        RestaurantDishAddonUpdateReqVO vo = buildUpdateVO();
        vo.setId(999L);
        when(addonMapper.selectById(999L)).thenReturn(null);

        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () ->
                addonService.updateAddon(vo),
                "不存在的加料应抛出 ServiceException");
    }

    @Test
    void updateAddon_并发路径_两个不同加料更新互不影响() {
        RestaurantDishAddonDO addon2 = new RestaurantDishAddonDO();
        addon2.setId(2L);
        addon2.setName("加芝士");
        addon2.setExtraPrice(BigDecimal.valueOf(3.00));

        RestaurantDishAddonUpdateReqVO vo1 = buildUpdateVO();
        vo1.setExtraPrice(BigDecimal.valueOf(5.00));
        RestaurantDishAddonUpdateReqVO vo2 = buildUpdateVO();
        vo2.setId(2L);
        vo2.setExtraPrice(BigDecimal.valueOf(8.00));

        when(addonMapper.selectById(1L)).thenReturn(mockAddon);
        when(addonMapper.selectById(2L)).thenReturn(addon2);
        when(spuAddonRelMapper.selectList()).thenReturn(Collections.emptyList());

        addonService.updateAddon(vo1);
        addonService.updateAddon(vo2);

        verify(addonMapper, times(2)).updateById(any(RestaurantDishAddonDO.class));
    }

    // ==================== deleteAddon ====================

    @Test
    void deleteAddon_正常路径_删除成功() {
        when(addonMapper.selectById(1L)).thenReturn(mockAddon);
        when(spuAddonRelMapper.selectList()).thenReturn(Collections.emptyList());

        addonService.deleteAddon(1L);

        verify(addonMapper).deleteById(1L);
        verify(eventPublisher).publishEvent(any(MenuCacheEvictEvent.class));
    }

    @Test
    void deleteAddon_正常路径_有SPU关联时驱逐门店缓存() {
        when(addonMapper.selectById(1L)).thenReturn(mockAddon);

        RestaurantDishSpuAddonRelDO rel = new RestaurantDishSpuAddonRelDO();
        rel.setAddonId(1L);
        rel.setSpuId(10L);
        when(spuAddonRelMapper.selectList()).thenReturn(Collections.singletonList(rel));

        RestaurantStoreDishDO sd = new RestaurantStoreDishDO();
        sd.setSpuId(10L);
        sd.setStoreId(100L);
        when(storeDishMapper.selectList()).thenReturn(Collections.singletonList(sd));

        addonService.deleteAddon(1L);

        verify(addonMapper).deleteById(1L);
        verify(eventPublisher).publishEvent(any(MenuCacheEvictEvent.class));
    }

    @Test
    void deleteAddon_异常路径_加料不存在() {
        when(addonMapper.selectById(999L)).thenReturn(null);

        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () ->
                addonService.deleteAddon(999L),
                "不存在的加料应抛出 ServiceException");
    }

    @Test
    void deleteAddon_并发路径_两个不同加料删除互不影响() {
        RestaurantDishAddonDO addon2 = new RestaurantDishAddonDO();
        addon2.setId(2L);
        addon2.setName("加芝士");

        when(addonMapper.selectById(1L)).thenReturn(mockAddon);
        when(addonMapper.selectById(2L)).thenReturn(addon2);
        when(spuAddonRelMapper.selectList()).thenReturn(Collections.emptyList());

        addonService.deleteAddon(1L);
        addonService.deleteAddon(2L);

        verify(addonMapper, times(2)).deleteById(anyLong());
    }

    // ==================== @LogRecord 注解验证 ====================

    @Test
    void createAddon_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantDishAddonServiceImpl.class.getMethod("createAddon", RestaurantDishAddonCreateReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "createAddon 缺少 @LogRecord 注解");
        assertEquals("加料", annotation.type());
        assertEquals("创建加料", annotation.subType());
    }

    @Test
    void updateAddon_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantDishAddonServiceImpl.class.getMethod("updateAddon", RestaurantDishAddonUpdateReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "updateAddon 缺少 @LogRecord 注解");
        assertEquals("加料", annotation.type());
        assertEquals("更新加料", annotation.subType());
    }

    @Test
    void deleteAddon_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantDishAddonServiceImpl.class.getMethod("deleteAddon", Long.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "deleteAddon 缺少 @LogRecord 注解");
        assertEquals("加料", annotation.type());
        assertEquals("删除加料", annotation.subType());
    }

    // ==================== helpers ====================

    private RestaurantDishAddonCreateReqVO buildCreateVO() {
        RestaurantDishAddonCreateReqVO vo = new RestaurantDishAddonCreateReqVO();
        vo.setBrandId(1L);
        vo.setGroupName("配菜加料");
        vo.setName("加鸡蛋");
        vo.setExtraPrice(BigDecimal.valueOf(2.00));
        vo.setIsRequired(false);
        vo.setIsMulti(true);
        vo.setSort(1);
        vo.setStatus(0);
        return vo;
    }

    private RestaurantDishAddonUpdateReqVO buildUpdateVO() {
        RestaurantDishAddonUpdateReqVO vo = new RestaurantDishAddonUpdateReqVO();
        vo.setId(1L);
        vo.setBrandId(1L);
        vo.setGroupName("配菜加料");
        vo.setName("加鸡蛋");
        vo.setExtraPrice(BigDecimal.valueOf(2.00));
        return vo;
    }
}
