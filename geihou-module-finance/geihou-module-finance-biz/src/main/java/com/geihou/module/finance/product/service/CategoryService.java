package com.geihou.module.finance.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.controller.admin.vo.CategoryRespVO;
import com.geihou.module.finance.product.dal.dataobject.ProductCategoryDO;
import com.geihou.module.finance.product.dal.mapper.ProductCategoryMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Category service for product category tree operations.
 *
 * <p>Tenant isolation is enforced via MyBatis-Plus TenantLineInnerInterceptor
 * and explicit tenant_id from TenantContextHolder.
 */
@Service
public class CategoryService {

    private final ProductCategoryMapper categoryMapper;

    public CategoryService(ProductCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    /**
     * Build the category tree for the current tenant.
     *
     * @return list of top-level categories with nested children
     */
    public List<CategoryRespVO> getCategoryTree() {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        List<ProductCategoryDO> allCategories = categoryMapper.selectList(
                new LambdaQueryWrapper<ProductCategoryDO>()
                        .orderByAsc(ProductCategoryDO::getSortOrder)
                        .orderByAsc(ProductCategoryDO::getId));

        Map<Long, CategoryRespVO> voMap = new HashMap<>();
        List<CategoryRespVO> roots = new ArrayList<>();

        // First pass: create all VOs
        for (ProductCategoryDO category : allCategories) {
            CategoryRespVO vo = toRespVO(category);
            voMap.put(vo.getId(), vo);
        }

        // Second pass: build tree
        for (ProductCategoryDO category : allCategories) {
            CategoryRespVO vo = voMap.get(category.getId());
            if (category.getParentCategoryId() == null) {
                roots.add(vo);
            } else {
                CategoryRespVO parent = voMap.get(category.getParentCategoryId());
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    // Parent not found (possibly deleted), treat as root
                    roots.add(vo);
                }
            }
        }

        return roots;
    }

    /**
     * Get a category by ID within the current tenant.
     */
    public ProductCategoryDO getCategory(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");
        return categoryMapper.selectById(id);
    }

    private CategoryRespVO toRespVO(ProductCategoryDO category) {
        CategoryRespVO vo = new CategoryRespVO();
        vo.setId(category.getId());
        vo.setCategoryCode(category.getCategoryCode());
        vo.setCategoryName(category.getCategoryName());
        vo.setParentCategoryId(category.getParentCategoryId());
        vo.setCategoryPath(category.getCategoryPath());
        vo.setLevel(category.getLevel());
        vo.setIcon(category.getIcon());
        vo.setSortOrder(category.getSortOrder());
        vo.setStatus(category.getStatus());
        return vo;
    }
}
