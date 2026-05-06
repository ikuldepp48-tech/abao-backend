package cn.iocoder.yudao.module.consulting.controller.admin.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "管理后台 - 客户全景视图 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientPanoramaRespVO {

    @Schema(description = "客户ID", example = "1")
    private Long clientId;

    @Schema(description = "客户名称", example = "阿堡餐饮")
    private String clientName;

    @Schema(description = "简称", example = "阿堡")
    private String shortName;

    @Schema(description = "行业", example = "餐饮")
    private String industry;

    @Schema(description = "综合健康分 (0-100)", example = "65")
    private Integer score;

    @Schema(description = "健康度等级: green / yellow / red", example = "yellow")
    private String healthLevel;

    @Schema(description = "进行中项目数", example = "2")
    private Integer activeEngagementCount;

    @Schema(description = "项目名称列表", example = "[\"年度顾问\", \"菜单优化\"]")
    private List<String> engagementTitles;

    @Schema(description = "门店数", example = "5")
    private Integer storeCount;

    @Schema(description = "客户状态", example = "1")
    private Integer status;

    @Schema(description = "所有客户平均分", example = "62")
    private Integer avgScore;

}
