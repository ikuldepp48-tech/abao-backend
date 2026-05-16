package cn.iocoder.yudao.module.restaurant.service.brand;

import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.brand.RestaurantBrandDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.brand.RestaurantBrandMapper;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RestaurantBrandServiceImpl 单元测试
 *
 * 测试覆盖：正常路径 / @LogRecord 注解验证
 * 覆盖方法：createBrand / updateBrand / deleteBrand
 */
@ExtendWith(MockitoExtension.class)
class RestaurantBrandServiceImplTest {

    @Mock
    private RestaurantBrandMapper restaurantBrandMapper;

    @InjectMocks
    private RestaurantBrandServiceImpl brandService;

    private RestaurantBrandDO mockBrand;

    @BeforeEach
    void setUp() {
        mockBrand = new RestaurantBrandDO();
        mockBrand.setId(1L);
        mockBrand.setName("阿堡");
        mockBrand.setCode("abao");
        mockBrand.setLogo("https://xxx.com/logo.png");
        mockBrand.setDescription("中式快餐品牌");
        mockBrand.setCategory("中式快餐");
        mockBrand.setStatus(0);
    }

    // ==================== createBrand ====================

    @Test
    void createBrand_正常路径_创建成功() {
        RestaurantBrandCreateReqVO vo = buildCreateVO();

        Long id = brandService.createBrand(vo);

        verify(restaurantBrandMapper).insert(any(RestaurantBrandDO.class));
    }

    // ==================== updateBrand ====================

    @Test
    void updateBrand_正常路径_更新成功() {
        RestaurantBrandUpdateReqVO vo = buildUpdateVO();
        when(restaurantBrandMapper.selectById(1L)).thenReturn(mockBrand);

        brandService.updateBrand(vo);

        verify(restaurantBrandMapper).updateById(any(RestaurantBrandDO.class));
    }

    // ==================== deleteBrand ====================

    @Test
    void deleteBrand_正常路径_删除成功() {
        when(restaurantBrandMapper.selectById(1L)).thenReturn(mockBrand);

        brandService.deleteBrand(1L);

        verify(restaurantBrandMapper).deleteById(1L);
    }

    // ==================== @LogRecord 注解验证 ====================

    @Test
    void createBrand_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantBrandServiceImpl.class.getMethod("createBrand", RestaurantBrandCreateReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "createBrand 缺少 @LogRecord 注解");
        assertEquals("品牌", annotation.type());
        assertEquals("创建品牌", annotation.subType());
    }

    @Test
    void updateBrand_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantBrandServiceImpl.class.getMethod("updateBrand", RestaurantBrandUpdateReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "updateBrand 缺少 @LogRecord 注解");
        assertEquals("品牌", annotation.type());
        assertEquals("更新品牌", annotation.subType());
    }

    @Test
    void deleteBrand_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantBrandServiceImpl.class.getMethod("deleteBrand", Long.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "deleteBrand 缺少 @LogRecord 注解");
        assertEquals("品牌", annotation.type());
        assertEquals("删除品牌", annotation.subType());
    }

    // ==================== helpers ====================

    private RestaurantBrandCreateReqVO buildCreateVO() {
        RestaurantBrandCreateReqVO vo = new RestaurantBrandCreateReqVO();
        vo.setName("阿堡");
        vo.setCode("abao");
        vo.setLogo("https://xxx.com/logo.png");
        vo.setDescription("中式快餐品牌");
        vo.setCategory("中式快餐");
        vo.setStatus(0);
        return vo;
    }

    private RestaurantBrandUpdateReqVO buildUpdateVO() {
        RestaurantBrandUpdateReqVO vo = new RestaurantBrandUpdateReqVO();
        vo.setId(1L);
        vo.setName("阿堡");
        vo.setCode("abao");
        vo.setLogo("https://xxx.com/logo.png");
        vo.setDescription("中式快餐品牌");
        vo.setCategory("中式快餐");
        vo.setStatus(0);
        return vo;
    }
}
