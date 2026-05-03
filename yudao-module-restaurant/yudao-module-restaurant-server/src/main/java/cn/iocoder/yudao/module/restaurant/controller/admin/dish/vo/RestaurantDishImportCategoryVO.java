package cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class RestaurantDishImportCategoryVO {

    @ExcelProperty("一级分类")
    private String level1Category;

    @ExcelProperty("二级分类")
    private String level2Category;

    @ExcelProperty("三级分类")
    private String level3Category;

    @ExcelProperty("排序")
    private Integer sort;

}
