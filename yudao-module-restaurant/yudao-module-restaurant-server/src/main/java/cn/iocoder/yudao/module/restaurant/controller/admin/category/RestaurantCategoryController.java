package cn.iocoder.yudao.module.restaurant.controller.admin.category;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryPageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryRespVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.category.vo.RestaurantCategoryUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.convert.category.RestaurantCategoryConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.category.RestaurantCategoryDO;
import cn.iocoder.yudao.module.restaurant.service.category.RestaurantCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 菜品分类")
@RestController
@RequestMapping("/restaurant/category")
@Validated
public class RestaurantCategoryController {

    @Resource
    private RestaurantCategoryService categoryService;

    @PostMapping("/create")
    @Operation(summary = "创建菜品分类")
    @PreAuthorize("@ss.hasPermission('restaurant:category:create')")
    public CommonResult<Long> createCategory(@Valid @RequestBody RestaurantCategoryCreateReqVO createReqVO) {
        return success(categoryService.createCategory(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新菜品分类")
    @PreAuthorize("@ss.hasPermission('restaurant:category:update')")
    public CommonResult<Boolean> updateCategory(@Valid @RequestBody RestaurantCategoryUpdateReqVO updateReqVO) {
        categoryService.updateCategory(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除菜品分类")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('restaurant:category:delete')")
    public CommonResult<Boolean> deleteCategory(@RequestParam("id") Long id) {
        categoryService.deleteCategory(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得菜品分类")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('restaurant:category:query')")
    public CommonResult<RestaurantCategoryRespVO> getCategory(@RequestParam("id") Long id) {
        RestaurantCategoryDO category = categoryService.getCategory(id);
        return success(RestaurantCategoryConvert.INSTANCE.convert(category));
    }

    @GetMapping("/list-all-simple")
    @Operation(summary = "获取菜品分类精简信息列表", description = "主要用于前端的下拉选项")
    public CommonResult<List<RestaurantCategoryRespVO>> getSimpleCategoryList() {
        List<RestaurantCategoryDO> list = categoryService.getCategoryList();
        return success(RestaurantCategoryConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/list")
    @Operation(summary = "获得菜品分类列表")
    @Parameter(name = "ids", description = "编号列表", required = true, example = "1,2")
    @PreAuthorize("@ss.hasPermission('restaurant:category:query')")
    public CommonResult<List<RestaurantCategoryRespVO>> getCategoryList(@RequestParam("ids") Collection<Long> ids) {
        List<RestaurantCategoryDO> list = categoryService.getCategoryList(ids);
        return success(RestaurantCategoryConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/page")
    @Operation(summary = "获得菜品分类分页")
    @PreAuthorize("@ss.hasPermission('restaurant:category:query')")
    public CommonResult<PageResult<RestaurantCategoryRespVO>> getCategoryPage(@Valid RestaurantCategoryPageReqVO pageVO) {
        PageResult<RestaurantCategoryDO> pageResult = categoryService.getCategoryPage(pageVO);
        return success(RestaurantCategoryConvert.INSTANCE.convertPage(pageResult));
    }

}
