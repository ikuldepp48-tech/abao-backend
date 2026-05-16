package cn.iocoder.yudao.module.restaurant.service.category;

import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.category.RestaurantCategoryDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.category.RestaurantCategoryMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSpuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreMapper;
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
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RestaurantCategoryServiceImpl 单元测试
 *
 * 测试覆盖：正常路径 / @LogRecord 注解验证
 * 覆盖方法：createCategory / updateCategory / deleteCategory
 */
@ExtendWith(MockitoExtension.class)
class RestaurantCategoryServiceImplTest {

    @Mock
    private RestaurantCategoryMapper restaurantCategoryMapper;

    @Mock
    private RestaurantDishSpuMapper dishSpuMapper;

    @Mock
    private RestaurantStoreMapper storeMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RestaurantCategoryServiceImpl categoryService;

    private RestaurantCategoryDO mockCategory;

    @BeforeEach
    void setUp() {
        mockCategory = new RestaurantCategoryDO();
        mockCategory.setId(1L);
        mockCategory.setName("汉堡");
        mockCategory.setParentId(0L);
        mockCategory.setSort(1);
        mockCategory.setStatus(0);
    }

    // ==================== createCategory ====================

    @Test
    void createCategory_正常路径_创建成功() {
        RestaurantCategoryCreateReqVO vo = buildCreateVO();

        Long id = categoryService.createCategory(vo);

        verify(restaurantCategoryMapper).insert(any(RestaurantCategoryDO.class));
    }

    // ==================== updateCategory ====================

    @Test
    void updateCategory_正常路径_更新成功() {
        RestaurantCategoryUpdateReqVO vo = buildUpdateVO();
        when(restaurantCategoryMapper.selectById(1L)).thenReturn(mockCategory);

        RestaurantStoreDO storeDO = new RestaurantStoreDO();
        storeDO.setId(1L);
        when(storeMapper.selectList()).thenReturn(Collections.singletonList(storeDO));

        categoryService.updateCategory(vo);

        verify(restaurantCategoryMapper).updateById(any(RestaurantCategoryDO.class));
        verify(eventPublisher).publishEvent(any(MenuCacheEvictEvent.class));
    }

    // ==================== deleteCategory ====================

    @Test
    void deleteCategory_正常路径_删除成功() {
        when(restaurantCategoryMapper.selectById(1L)).thenReturn(mockCategory);
        when(restaurantCategoryMapper.selectCount(any())).thenReturn(0L);
        when(dishSpuMapper.selectCount(any())).thenReturn(0L);

        categoryService.deleteCategory(1L);

        verify(restaurantCategoryMapper).deleteById(1L);
    }

    // ==================== @LogRecord 注解验证 ====================

    @Test
    void createCategory_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantCategoryServiceImpl.class.getMethod("createCategory", RestaurantCategoryCreateReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "createCategory 缺少 @LogRecord 注解");
        assertEquals("分类", annotation.type());
        assertEquals("创建分类", annotation.subType());
    }

    @Test
    void updateCategory_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantCategoryServiceImpl.class.getMethod("updateCategory", RestaurantCategoryUpdateReqVO.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "updateCategory 缺少 @LogRecord 注解");
        assertEquals("分类", annotation.type());
        assertEquals("更新分类", annotation.subType());
    }

    @Test
    void deleteCategory_检查LogRecord注解存在() throws NoSuchMethodException {
        Method method = RestaurantCategoryServiceImpl.class.getMethod("deleteCategory", Long.class);
        LogRecord annotation = method.getAnnotation(LogRecord.class);
        assertNotNull(annotation, "deleteCategory 缺少 @LogRecord 注解");
        assertEquals("分类", annotation.type());
        assertEquals("删除分类", annotation.subType());
    }

    // ==================== helpers ====================

    private RestaurantCategoryCreateReqVO buildCreateVO() {
        RestaurantCategoryCreateReqVO vo = new RestaurantCategoryCreateReqVO();
        vo.setName("汉堡");
        vo.setParentId(0L);
        vo.setSort(1);
        vo.setStatus(0);
        return vo;
    }

    private RestaurantCategoryUpdateReqVO buildUpdateVO() {
        RestaurantCategoryUpdateReqVO vo = new RestaurantCategoryUpdateReqVO();
        vo.setId(1L);
        vo.setName("汉堡");
        vo.setParentId(0L);
        vo.setSort(1);
        vo.setStatus(0);
        return vo;
    }
}
