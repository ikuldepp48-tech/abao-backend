package cn.iocoder.yudao.module.restaurant.controller.admin.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - 首页看板数据")
@Data
public class DashboardRespVO {

    @Schema(description = "品牌门店列表")
    private List<BrandStoreVO> brandStores;

    @Schema(description = "销售汇总")
    private SalesSummaryVO salesSummary;

    @Schema(description = "菜品销售排行")
    private List<DishRankItemVO> dishRanking;

    @Schema(description = "门店销售排行")
    private List<StoreRankItemVO> storeRanking;

    @Schema(description = "顾客反馈热门词")
    private List<HotWordVO> hotWords;

    // ========== 内嵌 VO ==========

    @Data
    @Schema(description = "品牌 + 门店信息")
    public static class BrandStoreVO {
        @Schema(description = "品牌ID")
        private Long brandId;

        @Schema(description = "品牌名称")
        private String brandName;

        @Schema(description = "品牌品类")
        private String category;

        @Schema(description = "门店ID")
        private Long storeId;

        @Schema(description = "门店名称")
        private String storeName;

        @Schema(description = "门店编码")
        private String storeCode;

        @Schema(description = "省")
        private String province;

        @Schema(description = "市")
        private String city;

        @Schema(description = "区")
        private String district;

        @Schema(description = "详细地址")
        private String address;

        @Schema(description = "联系电话")
        private String phone;

        @Schema(description = "营业时间")
        private String businessHours;

        @Schema(description = "面积(㎡)")
        private BigDecimal areaSize;

        @Schema(description = "座位数")
        private Integer seatCount;

        @Schema(description = "开业日期")
        private String openDate;

        @Schema(description = "支持堂食")
        private Boolean supportDineIn;

        @Schema(description = "支持外卖")
        private Boolean supportTakeout;

        @Schema(description = "支持自提")
        private Boolean supportPickup;
    }

    @Data
    @Schema(description = "销售汇总")
    public static class SalesSummaryVO {
        @Schema(description = "今日订单数")
        private Long todayOrderCount;

        @Schema(description = "今日销售额")
        private BigDecimal todaySales;

        @Schema(description = "本月订单数")
        private Long monthOrderCount;

        @Schema(description = "本月销售额")
        private BigDecimal monthSales;

        @Schema(description = "总门店数")
        private Long totalStores;

        @Schema(description = "营业中门店数")
        private Long activeStores;
    }

    @Data
    @Schema(description = "菜品销售排行项")
    public static class DishRankItemVO {
        @Schema(description = "菜品名称")
        private String dishName;

        @Schema(description = "销售数量")
        private Long quantity;

        @Schema(description = "销售金额")
        private BigDecimal amount;

        @Schema(description = "门店ID")
        private Long storeId;

        @Schema(description = "门店名称")
        private String storeName;
    }

    @Data
    @Schema(description = "门店销售排行项")
    public static class StoreRankItemVO {
        @Schema(description = "门店ID")
        private Long storeId;

        @Schema(description = "门店名称")
        private String storeName;

        @Schema(description = "订单数")
        private Long orderCount;

        @Schema(description = "销售额")
        private BigDecimal totalSales;
    }

    @Data
    @Schema(description = "热门词")
    public static class HotWordVO {
        @Schema(description = "词语")
        private String word;

        @Schema(description = "热度(越高越热)")
        private Integer heat;

        @Schema(description = "来源类型(菜品/加料/评价)")
        private String sourceType;
    }
}
