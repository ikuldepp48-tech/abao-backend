package cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RestaurantDishImportVO {

    @ExcelProperty("菜品名称")
    private String name;

    @ExcelProperty("分类名称")
    private String categoryName;

    @ExcelProperty("售价")
    private BigDecimal price;

    @ExcelProperty("描述")
    private String description;

    @ExcelProperty("图片URL")
    private String image;

    @ExcelProperty("排序")
    private Integer sort;

}
