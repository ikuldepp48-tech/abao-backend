package cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 加料分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RestaurantDishAddonPageReqVO extends PageParam {

    @Schema(description = "品牌ID", example = "1")
    private Long brandId;

    @Schema(description = "加料组名称", example = "配菜加料")
    private String groupName;

    @Schema(description = "加料名称", example = "加鸡蛋")
    private String name;

    @Schema(description = "状态：0-启用 1-禁用", example = "0")
    private Integer status;

}
