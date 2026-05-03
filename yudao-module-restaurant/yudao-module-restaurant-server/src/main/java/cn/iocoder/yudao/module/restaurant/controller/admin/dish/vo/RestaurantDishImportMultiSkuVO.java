package cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RestaurantDishImportMultiSkuVO {

    @ExcelProperty("菜品名")
    private String name;

    @ExcelProperty("SKU名")
    private String skuName;

    @ExcelProperty("规格属性")
    private String properties;

    @ExcelProperty("售价")
    private BigDecimal price;

}
