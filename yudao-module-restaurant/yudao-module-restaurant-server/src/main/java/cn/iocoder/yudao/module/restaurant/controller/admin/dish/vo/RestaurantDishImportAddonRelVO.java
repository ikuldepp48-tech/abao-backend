package cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class RestaurantDishImportAddonRelVO {

    @ExcelProperty("菜品名")
    private String name;

    @ExcelProperty("加料组名")
    private String addonGroupName;

}
