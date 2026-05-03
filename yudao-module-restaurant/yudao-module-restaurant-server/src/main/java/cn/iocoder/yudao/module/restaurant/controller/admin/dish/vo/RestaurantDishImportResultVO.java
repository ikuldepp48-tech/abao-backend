package cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RestaurantDishImportResultVO {

    @Schema(description = "成功导入条数", example = "25")
    private int successCount;

    @Schema(description = "跳过条数", example = "3")
    private int skipCount;

    @Schema(description = "失败条数", example = "2")
    private int failCount;

    @Schema(description = "失败明细")
    private List<FailDetail> failDetails = new ArrayList<>();

    @Data
    public static class FailDetail {
        @Schema(description = "Sheet名", example = "简单菜品")
        private String sheet;
        @Schema(description = "行号", example = "5")
        private int row;
        @Schema(description = "菜品名", example = "测试菜品")
        private String name;
        @Schema(description = "失败原因", example = "分类不存在")
        private String reason;
    }

    public void addFail(String sheet, int row, String name, String reason) {
        failDetails.add(new FailDetail() {{
            setSheet(sheet);
            setRow(row);
            setName(name);
            setReason(reason);
        }});
        failCount++;
    }

    public void addSkip() {
        skipCount++;
    }

    public void addSuccess() {
        successCount++;
    }

}
