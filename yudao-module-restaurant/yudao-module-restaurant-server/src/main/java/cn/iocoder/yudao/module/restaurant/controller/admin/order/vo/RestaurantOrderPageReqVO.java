package cn.iocoder.yudao.module.restaurant.controller.admin.order.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 订单分页查询 VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RestaurantOrderPageReqVO extends PageParam {

    @Schema(description = "订单号", example = "ORD202605050001")
    private String orderNo;

    @Schema(description = "门店ID", example = "1001")
    private Long storeId;

    @Schema(description = "订单状态", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
