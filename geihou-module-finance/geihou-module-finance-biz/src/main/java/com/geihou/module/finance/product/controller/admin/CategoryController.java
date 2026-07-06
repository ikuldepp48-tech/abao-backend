package com.geihou.module.finance.product.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.finance.product.controller.admin.vo.CategoryRespVO;
import com.geihou.module.finance.product.service.CategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Category admin controller.
 *
 * <p>Base path: /admin-api/finance/product/category_tree
 * Provides category tree query for customer/staff/admin shared use.
 */
@RestController
@RequestMapping("/admin-api/finance/product")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/category_tree")
    public CommonResult<List<CategoryRespVO>> getCategoryTree() {
        List<CategoryRespVO> tree = categoryService.getCategoryTree();
        return CommonResult.success(tree);
    }
}
