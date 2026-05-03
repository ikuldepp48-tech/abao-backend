package cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RestaurantDishImportSimpleDishVO {

    @ExcelProperty("一级分类")
    private String level1Category;

    @ExcelProperty("二级分类")
    private String level2Category;

    @ExcelProperty("菜品名")
    private String name;

    @ExcelProperty("副标题")
    private String subtitle;

    @ExcelProperty("售价")
    private BigDecimal price;

    @ExcelProperty("单位")
    private String unit;

    @ExcelProperty("是否推荐")
    private String recommended;

    @ExcelProperty("是否新品")
    private String isNew;

    @ExcelProperty("是否招牌")
    private String isSignature;

    @ExcelProperty("描述")
    private String description;

    @ExcelProperty("图片文件名")
    private String imageFileName;

}
