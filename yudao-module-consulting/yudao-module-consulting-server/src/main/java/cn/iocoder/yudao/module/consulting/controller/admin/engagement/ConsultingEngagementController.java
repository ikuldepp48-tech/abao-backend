package cn.iocoder.yudao.module.consulting.controller.admin.engagement;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.consulting.controller.admin.engagement.vo.*;
import cn.iocoder.yudao.module.consulting.convert.engagement.ConsultingEngagementConvert;
import cn.iocoder.yudao.module.consulting.dal.dataobject.engagement.ConsultingEngagementDO;
import cn.iocoder.yudao.module.consulting.service.engagement.ConsultingEngagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.*;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;

@Tag(name = "管理后台 - 咨询项目")
@RestController
@RequestMapping("/consulting/engagement")
@Validated
public class ConsultingEngagementController {

    @Resource
    private ConsultingEngagementService engagementService;

    @ApiAccessLog(operateType = CREATE)
    @PostMapping("/create")
    @Operation(summary = "创建咨询项目")
    @PreAuthorize("@ss.hasPermission('consulting:engagement:create')")
    public CommonResult<Long> createEngagement(@Valid @RequestBody ConsultingEngagementCreateReqVO createReqVO) {
        return success(engagementService.createEngagement(createReqVO));
    }

    @ApiAccessLog(operateType = UPDATE)
    @PutMapping("/update")
    @Operation(summary = "更新咨询项目")
    @PreAuthorize("@ss.hasPermission('consulting:engagement:update')")
    public CommonResult<Boolean> updateEngagement(@Valid @RequestBody ConsultingEngagementUpdateReqVO updateReqVO) {
        engagementService.updateEngagement(updateReqVO);
        return success(true);
    }

    @ApiAccessLog(operateType = DELETE)
    @DeleteMapping("/delete")
    @Operation(summary = "删除咨询项目")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('consulting:engagement:delete')")
    public CommonResult<Boolean> deleteEngagement(@RequestParam("id") Long id) {
        engagementService.deleteEngagement(id);
        return success(true);
    }

    @ApiAccessLog(operateType = GET)
    @GetMapping("/get")
    @Operation(summary = "获得咨询项目")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('consulting:engagement:query')")
    public CommonResult<ConsultingEngagementRespVO> getEngagement(@RequestParam("id") Long id) {
        ConsultingEngagementDO engagement = engagementService.getEngagement(id);
        return success(ConsultingEngagementConvert.INSTANCE.convert(engagement));
    }

    @ApiAccessLog(operateType = GET)
    @GetMapping("/page")
    @Operation(summary = "获得咨询项目分页")
    @PreAuthorize("@ss.hasPermission('consulting:engagement:query')")
    public CommonResult<PageResult<ConsultingEngagementRespVO>> getEngagementPage(@Valid ConsultingEngagementPageReqVO pageVO) {
        PageResult<ConsultingEngagementDO> pageResult = engagementService.getEngagementPage(pageVO);
        return success(ConsultingEngagementConvert.INSTANCE.convertPage(pageResult));
    }

    @ApiAccessLog(operateType = UPDATE)
    @PutMapping("/advance-phase")
    @Operation(summary = "推进项目阶段")
    @PreAuthorize("@ss.hasPermission('consulting:engagement:update')")
    public CommonResult<Boolean> advancePhase(@RequestParam("id") Long id,
                                               @RequestParam("phase") Integer phase) {
        engagementService.advancePhase(id, phase);
        return success(true);
    }

}
