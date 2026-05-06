package cn.iocoder.yudao.module.consulting.controller.admin.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "管理后台 - 客户健康度排行项 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientRankingItemVO {

    @Schema(description = "客户ID", example = "1")
    private Long clientId;

    @Schema(description = "客户名称", example = "XX餐饮公司")
    private String clientName;

    @Schema(description = "综合健康分 (0-100)", example = "65")
    private Integer score;

    @Schema(description = "沟通频率分 (0-40)", example = "20")
    private Integer communicationScore;

    @Schema(description = "项目活跃分 (0-30)", example = "25")
    private Integer projectScore;

    @Schema(description = "合同剩余分 (0-30)", example = "20")
    private Integer contractScore;

    @Schema(description = "健康度等级: green / yellow / red", example = "yellow")
    private String healthLevel;

    @Schema(description = "紧急操作建议，空字符串表示无需紧急操作", example = "立即跟进")
    private String urgentAction;

    @Schema(description = "预警原因列表", example = "[\"30天未沟通\", \"合同即将到期\"]")
    private List<String> warnings;

    @Schema(description = "所有客户平均分", example = "62")
    private Integer avgScore;

}
