package cn.iocoder.yudao.module.restaurant.controller.app.menu.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AppMenuRespVO {

    @Schema(description = "门店信息")
    private StoreInfo store;

    @Schema(description = "分类树")
    private List<CategoryMenu> categories;

    @Schema(description = "套餐列表")
    private List<ComboItem> combos;

    // --- inner types ---

    @Data
    public static class StoreInfo {
        @Schema(description = "门店ID", example = "1001")
        private Long id;
        @Schema(description = "门店名称", example = "阿堡总店")
        private String name;
        @Schema(description = "营业时间", example = "10:00-22:00")
        private String businessHours;
        @Schema(description = "是否营业中")
        private Boolean isOpen;
    }

    @Data
    public static class CategoryMenu {
        @Schema(description = "分类ID", example = "1")
        private Long id;
        @Schema(description = "分类名称", example = "主食")
        private String name;
        @Schema(description = "层级", example = "1")
        private Integer level;
        @Schema(description = "排序", example = "1")
        private Integer sort;
        @Schema(description = "子分类")
        private List<CategoryMenu> children;
        @Schema(description = "菜品列表（仅叶子分类有）")
        private List<DishItem> dishes;
    }

    @Data
    public static class DishItem {
        @Schema(description = "菜品ID", example = "1")
        private Long spuId;
        @Schema(description = "菜品名称", example = "招牌牛肉饭")
        private String name;
        @Schema(description = "封面图URL")
        private String coverUrl;
        @Schema(description = "副标题", example = "经典招牌")
        private String subtitle;
        @Schema(description = "最低价", example = "22.00")
        private BigDecimal minPrice;
        @Schema(description = "最高价", example = "28.00")
        private BigDecimal maxPrice;
        @Schema(description = "是否招牌")
        private Boolean isSignature;
        @Schema(description = "是否新品")
        private Boolean isNew;
        @Schema(description = "是否沽清")
        private Boolean isSoldOut;
        @Schema(description = "标签", example = "[\"招牌\",\"辣\"]")
        private List<String> tags;
        @Schema(description = "排序", example = "1")
        private Integer sort;
        @Schema(description = "SKU列表")
        private List<SkuItem> skus;
        @Schema(description = "加料组列表")
        private List<AddonGroup> addons;
    }

    @Data
    public static class SkuItem {
        @Schema(description = "SKU ID", example = "1")
        private Long id;
        @Schema(description = "SKU名称", example = "大碗")
        private String name;
        @Schema(description = "售价", example = "28.00")
        private BigDecimal price;
        @Schema(description = "规格属性 JSON", example = "{\"size\":\"大碗\"}")
        private String properties;
    }

    @Data
    public static class AddonGroup {
        @Schema(description = "加料组名称", example = "辣度")
        private String groupName;
        @Schema(description = "是否必选")
        private Boolean isRequired;
        @Schema(description = "是否可多选")
        private Boolean isMulti;
        @Schema(description = "加料选项")
        private List<AddonOption> options;
    }

    @Data
    public static class AddonOption {
        @Schema(description = "加料ID", example = "101")
        private Long id;
        @Schema(description = "加料名称", example = "微辣")
        private String name;
        @Schema(description = "额外加价", example = "0.00")
        private BigDecimal extraPrice;
    }

    @Data
    public static class ComboItem {
        @Schema(description = "套餐ID", example = "5001")
        private Long comboId;
        @Schema(description = "套餐名称", example = "双人套餐")
        private String name;
        @Schema(description = "封面图URL")
        private String coverUrl;
        @Schema(description = "套餐价", example = "58.00")
        private BigDecimal price;
        @Schema(description = "原价", example = "70.00")
        private BigDecimal originalPrice;
        @Schema(description = "套餐描述")
        private String description;
        @Schema(description = "套餐包含菜品")
        private List<ComboItemDetail> items;
    }

    @Data
    public static class ComboItemDetail {
        @Schema(description = "SPU ID")
        private Long spuId;
        @Schema(description = "菜品名")
        private String spuName;
        @Schema(description = "SKU ID")
        private Long skuId;
        @Schema(description = "SKU名")
        private String skuName;
        @Schema(description = "数量")
        private Integer quantity;
        @Schema(description = "额外加价")
        private BigDecimal extraPrice;
    }

}
