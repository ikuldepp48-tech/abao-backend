package com.geihou.module.finance.product.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.ProductTestConfig;
import com.geihou.module.finance.product.TestSchemaInitializer;
import com.geihou.module.finance.product.controller.admin.vo.CategoryRespVO;
import com.geihou.module.finance.product.dal.dataobject.ProductCategoryDO;
import com.geihou.module.finance.product.dal.mapper.ProductCategoryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Category service integration test.
 *
 * <p>Tests category tree building with multi-level hierarchy.
 */
@SpringBootTest(
        classes = ProductTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:cat_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CategoryServiceTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductCategoryMapper categoryMapper;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        TestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void getCategoryTreeShouldReturnEmptyWhenNoCategories() {
        List<CategoryRespVO> tree = categoryService.getCategoryTree();
        assertThat(tree).isEmpty();
    }

    @Test
    void getCategoryTreeShouldBuildMultiLevelTree() {
        // Create root category
        ProductCategoryDO root = createCategory("FOOD", "Food", null, 1, 0);
        categoryMapper.insert(root);

        // Create child
        ProductCategoryDO child = createCategory("BURGERS", "Burgers", root.getId(), 2, 0);
        categoryMapper.insert(child);

        // Create grandchild
        ProductCategoryDO grandchild = createCategory("BEEF_BURGERS", "Beef Burgers", child.getId(), 3, 0);
        categoryMapper.insert(grandchild);

        List<CategoryRespVO> tree = categoryService.getCategoryTree();
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getCategoryCode()).isEqualTo("FOOD");
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getCategoryCode()).isEqualTo("BURGERS");
        assertThat(tree.get(0).getChildren().get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getChildren().get(0).getCategoryCode()).isEqualTo("BEEF_BURGERS");
    }

    @Test
    void getCategoryTreeShouldReturnMultipleRoots() {
        ProductCategoryDO root1 = createCategory("FOOD", "Food", null, 1, 0);
        categoryMapper.insert(root1);

        ProductCategoryDO root2 = createCategory("DRINKS", "Drinks", null, 1, 1);
        categoryMapper.insert(root2);

        List<CategoryRespVO> tree = categoryService.getCategoryTree();
        assertThat(tree).hasSize(2);
        assertThat(tree).extracting(CategoryRespVO::getCategoryCode)
                .containsExactly("FOOD", "DRINKS");
    }

    @Test
    void getCategoryShouldReturnCategory() {
        ProductCategoryDO category = createCategory("SINGLE", "Single", null, 1, 0);
        categoryMapper.insert(category);

        ProductCategoryDO found = categoryService.getCategory(category.getId());
        assertThat(found).isNotNull();
        assertThat(found.getCategoryCode()).isEqualTo("SINGLE");
    }

    private ProductCategoryDO createCategory(String code, String name, Long parentId, int level, int sortOrder) {
        ProductCategoryDO category = new ProductCategoryDO();
        category.setTenantId(1L);
        category.setCategoryCode(code);
        category.setCategoryName(name);
        category.setParentCategoryId(parentId);
        category.setLevel(level);
        category.setSortOrder(sortOrder);
        category.setStatus("ACTIVE");
        category.setCreator("");
        category.setCreateTime(LocalDateTime.now());
        category.setUpdater("");
        category.setUpdateTime(LocalDateTime.now());
        category.setDeleted(false);
        return category;
    }
}
