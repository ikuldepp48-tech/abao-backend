package cn.iocoder.yudao.module.restaurant.controller.app.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "顾客端 - 订单创建 VO")
@Data
public class AppOrderCreateReqVO {

    @Schema(description = "门店ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    @NotNull
    private Long storeId;

    @Schema(description = "订单类型：1堂食 2外卖 3自取", example = "1")
    private Integer orderType = 1;

    @Schema(description = "桌台ID（堂食必填）", example = "5001")
    private Long tableId;

    @Schema(description = "就餐人数", example = "4")
    private Integer dinerCount = 1;

    @Schema(description = "菜品明细", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty
    private List<OrderItem> items;

    @Schema(description = "订单备注", example = "靠窗位置")
    private String remark;

    @Schema(description = "客户端幂等key", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String clientOrderNo;

    @Data
    @Schema(description = "订单菜品明细")
    public static class OrderItem {
        @Schema(description = "SKU ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        private Long skuId;

        @Schema(description = "数量", example = "2")
        private Integer quantity = 1;

        @Schema(description = "加料列表")
        private List<AddonItem> addons;

        @Schema(description = "顾客备注", example = "少辣")
        private String customerRemark;
    }

    @Data
    @Schema(description = "加料项")
    public static class AddonItem {
        @Schema(description = "加料ID")
        private Long id;

        @Schema(description = "加料名称")
        private String name;

        @Schema(description = "加料价格")
        private BigDecimal price;
    }

}
