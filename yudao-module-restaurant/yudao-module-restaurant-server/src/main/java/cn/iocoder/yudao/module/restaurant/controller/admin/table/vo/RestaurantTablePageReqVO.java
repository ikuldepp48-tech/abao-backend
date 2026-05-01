package cn.iocoder.yudao.module.restaurant.controller.admin.table.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 桌台分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RestaurantTablePageReqVO extends PageParam {

    @Schema(description = "所属门店ID", example = "1")
    private Long storeId;

    @Schema(description = "区域", example = "大厅")
    private String area;

    @Schema(description = "桌号", example = "A01")
    private String tableNo;

    @Schema(description = "状态：0-空闲 1-用餐中 2-待结账 3-已锁定", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
