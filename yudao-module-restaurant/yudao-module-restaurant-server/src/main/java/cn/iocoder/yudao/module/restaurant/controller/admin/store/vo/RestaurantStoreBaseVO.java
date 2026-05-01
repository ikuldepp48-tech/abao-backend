package cn.iocoder.yudao.module.restaurant.controller.admin.store.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class RestaurantStoreBaseVO {

    @Schema(description = "所属品牌ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属品牌不能为空")
    private Long brandId;

    @Schema(description = "门店名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "阿堡总店")
    @NotEmpty(message = "门店名称不能为空")
    private String name;

    @Schema(description = "门店编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "abao_001")
    @NotEmpty(message = "门店编码不能为空")
    private String code;

    @Schema(description = "门店类型：1-直营 2-加盟 3-联营 4-中央厨房 5-卫星店", example = "1")
    private Integer type;

    @Schema(description = "上级门店ID（卫星店挂中央厨房）")
    private Long parentStoreId;

    @Schema(description = "省份")
    private String province;

    @Schema(description = "城市")
    private String city;

    @Schema(description = "区县")
    private String district;

    @Schema(description = "详细地址")
    private String address;

    @Schema(description = "经度")
    private BigDecimal longitude;

    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "店长用户ID")
    private Long managerUserId;

    @Schema(description = "营业时间 JSON", example = "{\"open\":\"08:00\",\"close\":\"22:00\"}")
    private String businessHours;

    @Schema(description = "状态：0-营业 1-暂停 2-关闭", example = "0")
    private Integer status;

    @Schema(description = "面积（平米）")
    private BigDecimal areaSize;

    @Schema(description = "座位数")
    private Integer seatCount;

}
