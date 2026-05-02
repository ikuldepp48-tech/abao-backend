package cn.iocoder.yudao.module.restaurant.controller.admin.store.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 门店菜品配置分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RestaurantStoreDishPageReqVO extends PageParam {

    @Schema(description = "门店ID", example = "1")
    private Long storeId;

    @Schema(description = "菜品SPU ID", example = "1")
    private Long spuId;

    @Schema(description = "状态：0-启用 1-禁用", example = "0")
    private Integer status;

}
