package cn.iocoder.yudao.module.restaurant.service.store;

import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStoreCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStoreUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreMapper;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RestaurantStoreServiceImpl 单元测试
 *
 * 测试覆盖：正常路径 / @LogRecord 注解验证
 * 覆盖方法：createStore / updateStore / deleteStore
 */
@ExtendWith(MockitoExtension.class)
class RestaurantStoreServiceImplTest {

    @Mock
    private RestaurantStoreMapper restaurantStoreMapper;

    @InjectMocks
    private RestaurantStoreServiceImpl storeService;

    private RestaurantStoreDO mockStore;

    @BeforeEach
    void setUp() {
        mockStore = new RestaurantStoreDO();
        mockStore.setId(1L);
        mockStore.setBrandId(1L);
        mockStore.setName("阿堡总店");
        mockStore.setCode("abao_001");
        mockStore.setType(1);
        mockStore.setProvince("广东省");
        mockStore.setCity("深圳市");
        mockStore.setDistrict("南山区");
        mockStore.setAddress("科技园南区");
        mockStore.setLongitude(BigDecimal.valueOf(113.95));
        mockStore.setLatitude(BigDecimal.valueOf(22.54));
        mockStore.setPhone("0755-88888888");
        mockStore.setBusinessHours("{\"open\":\"08:00\",\"close\":\"22:00\"}");
        mockStore.setStatus(0);
        mockStore.setAreaSize(BigDecimal.valueOf(120.00));
        mockStore.setSeatCount(30);
    }

    // ==================== createStore ====================

    @Test
    void createStore_正常路径_创建成功() {
        RestaurantStoreCreateReqVO vo = buildCreateVO();

        Long id = storeService.createStore(vo);

        verify(restaurantStoreMapper).insert(any(RestaurantStoreDO.class));
    }

    // ==================== updateStore ====================

    @Test
    void updateStore_正常路径_更新成功() {
        RestaurantStoreUpdateReqVO vo = buildUpdateVO();
        when(restaurantStoreMapper.selectById(1L)).thenReturn(mockStore);

        storeService.updateStore(vo);

        verify(restaurantStoreMapper).updateById(any(RestaurantStoreDO.class));
    }

    // ==================== deleteStore ====================

    @Test
    void deleteStore_正常路径_删除成功() {
        when(restaurantStoreMapper.selectById(1L)).thenReturn(mockStore);

        storeService.deleteStore(1L);

        verify(restaurantStoreMapper).deleteById(1L);
    }

    // ==================== @LogRecord 注解验证 ====================

    @Test
    void createStore_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantStoreServiceImpl.class.getMethod("createStore", RestaurantStoreCreateReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "createStore 缺少 @LogRecord 注解");
        assertEquals("门店", annotation.type());
        assertEquals("创建门店", annotation.subType());
    }

    @Test
    void updateStore_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantStoreServiceImpl.class.getMethod("updateStore", RestaurantStoreUpdateReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "updateStore 缺少 @LogRecord 注解");
        assertEquals("门店", annotation.type());
        assertEquals("更新门店", annotation.subType());
    }

    @Test
    void deleteStore_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantStoreServiceImpl.class.getMethod("deleteStore", Long.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "deleteStore 缺少 @LogRecord 注解");
        assertEquals("门店", annotation.type());
        assertEquals("删除门店", annotation.subType());
    }

    // ==================== helpers ====================

    private RestaurantStoreCreateReqVO buildCreateVO() {
        RestaurantStoreCreateReqVO vo = new RestaurantStoreCreateReqVO();
        vo.setBrandId(1L);
        vo.setName("阿堡总店");
        vo.setCode("abao_001");
        vo.setType(1);
        vo.setProvince("广东省");
        vo.setCity("深圳市");
        vo.setDistrict("南山区");
        vo.setAddress("科技园南区");
        vo.setLongitude(BigDecimal.valueOf(113.95));
        vo.setLatitude(BigDecimal.valueOf(22.54));
        vo.setPhone("0755-88888888");
        vo.setBusinessHours("{\"open\":\"08:00\",\"close\":\"22:00\"}");
        vo.setStatus(0);
        vo.setAreaSize(BigDecimal.valueOf(120.00));
        vo.setSeatCount(30);
        return vo;
    }

    private RestaurantStoreUpdateReqVO buildUpdateVO() {
        RestaurantStoreUpdateReqVO vo = new RestaurantStoreUpdateReqVO();
        vo.setId(1L);
        vo.setBrandId(1L);
        vo.setName("阿堡总店");
        vo.setCode("abao_001");
        vo.setType(1);
        vo.setProvince("广东省");
        vo.setCity("深圳市");
        vo.setDistrict("南山区");
        vo.setAddress("科技园南区");
        vo.setLongitude(BigDecimal.valueOf(113.95));
        vo.setLatitude(BigDecimal.valueOf(22.54));
        vo.setPhone("0755-88888888");
        vo.setBusinessHours("{\"open\":\"08:00\",\"close\":\"22:00\"}");
        vo.setStatus(0);
        vo.setAreaSize(BigDecimal.valueOf(120.00));
        vo.setSeatCount(30);
        return vo;
    }
}
